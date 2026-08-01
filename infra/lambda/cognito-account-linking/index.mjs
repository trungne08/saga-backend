import { createHash } from "node:crypto";
import {
  AdminGetUserCommand,
  AdminLinkProviderForUserCommand,
  CognitoIdentityProviderClient,
  ListUsersCommand,
} from "@aws-sdk/client-cognito-identity-provider";

const EXTERNAL_TRIGGER = "PreSignUp_ExternalProvider";
const TRUSTED_PROVIDER = "Google";
const EXTERNAL_STATUS = "EXTERNAL_PROVIDER";
const USABLE_NATIVE_STATUSES = new Set([
  "CONFIRMED",
  "FORCE_CHANGE_PASSWORD",
  "RESET_REQUIRED",
]);
const PUBLIC_ERRORS = Object.freeze({
  INVALID_PROVIDER: "External identity cannot be linked",
  INVALID_EMAIL: "A verified provider email is required",
  IDENTITY_CONFLICT: "Account linking requires administrator review",
  CONFIGURATION: "Account linking is not configured",
  SERVICE_FAILURE: "Account linking could not be completed",
});

export class AccountLinkingError extends Error {
  constructor(category, publicMessage, options = undefined) {
    super(publicMessage, options);
    this.name = "AccountLinkingError";
    this.category = category;
    this.publicMessage = publicMessage;
  }
}

export function normalizeEmail(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

export function isVerified(value) {
  return value === true
    || (typeof value === "string" && value.trim().toLowerCase() === "true");
}

export function isTrustedProviderName(value) {
  return typeof value === "string"
    && value.toLowerCase() === TRUSTED_PROVIDER.toLowerCase();
}

export function hasTrustedProviderPrefix(userName) {
  if (typeof userName !== "string") {
    return false;
  }

  const separator = userName.indexOf("_");
  return separator > 0
    && isTrustedProviderName(userName.slice(0, separator));
}

export function parseExternalUsername(userName) {
  if (typeof userName !== "string") {
    throw linkingError("MALFORMED_PROVIDER_USERNAME", "INVALID_PROVIDER");
  }

  const separator = userName.indexOf("_");
  if (separator <= 0 || separator === userName.length - 1) {
    throw linkingError("MALFORMED_PROVIDER_USERNAME", "INVALID_PROVIDER");
  }

  const rawProviderName = userName.slice(0, separator);
  const providerSubject = userName.slice(separator + 1);
  if (!isTrustedProviderName(rawProviderName)) {
    throw linkingError("UNSUPPORTED_PROVIDER", "INVALID_PROVIDER");
  }
  if (
    !providerSubject.trim()
    || providerSubject.trim() !== providerSubject
    || providerSubject.length > 2048
    || /[\u0000-\u001f\u007f]/u.test(providerSubject)
  ) {
    throw linkingError("MALFORMED_PROVIDER_SUBJECT", "INVALID_PROVIDER");
  }

  return { providerName: TRUSTED_PROVIDER, providerSubject };
}

export function safeHash(value) {
  return createHash("sha256")
    .update(String(value), "utf8")
    .digest("hex")
    .slice(0, 16);
}

export function createHandler({
  client,
  logger = console,
} = {}) {
  if (!client || typeof client.send !== "function") {
    throw new TypeError("A Cognito Identity Provider client is required");
  }

  return async function accountLinkingHandler(event, context = {}) {
    const audit = {
      triggerSource: safeTriggerSource(event?.triggerSource),
      providerName: undefined,
      emailHash: undefined,
      destinationUserHash: undefined,
      linkResult: undefined,
      awsRequestId: safeRequestId(context?.awsRequestId),
      errorCategory: undefined,
    };

    if (event?.triggerSource !== EXTERNAL_TRIGGER) {
      audit.linkResult = "IGNORED_TRIGGER";
      writeLog(logger, "info", audit);
      return event;
    }

    try {
      const { providerName, providerSubject } = parseExternalUsername(
        event?.userName,
      );
      audit.providerName = providerName;

      const attributes = event?.request?.userAttributes ?? {};
      const email = normalizeEmail(attributes.email);
      audit.emailHash = email ? safeHash(email) : undefined;

      if (!isValidEmail(email)) {
        throw linkingError("MISSING_OR_MALFORMED_EMAIL", "INVALID_EMAIL");
      }
      if (!isVerified(attributes.email_verified)) {
        throw linkingError("UNVERIFIED_PROVIDER_EMAIL", "INVALID_EMAIL");
      }
      if (typeof event?.userPoolId !== "string" || !event.userPoolId.trim()) {
        throw linkingError("MISSING_USER_POOL_ID", "SERVICE_FAILURE");
      }

      await requireFederatedProfileNotCreated(
        client,
        event.userPoolId,
        event.userName,
      );

      const listedUsers = await listUsersByExactEmail(
        client,
        event.userPoolId,
        email,
      );
      const nativeSummaries = listedUsers.filter(
        (user) => !isFederatedProfile(user),
      );

      const destinations = [];
      for (const summary of nativeSummaries) {
        const inspectedUser = await getUser(
          client,
          event.userPoolId,
          summary?.Username,
        );
        if (!isFederatedProfile(inspectedUser)) {
          validateDestination(inspectedUser, email);
          destinations.push(inspectedUser);
        }
      }

      if (destinations.length === 0) {
        audit.linkResult = "NO_LOCAL_USER";
        writeLog(logger, "info", audit);
        return event;
      }
      if (destinations.length > 1) {
        throw linkingError("MULTIPLE_LOCAL_USERS", "IDENTITY_CONFLICT");
      }

      const destination = destinations[0];
      audit.destinationUserHash = safeHash(destination.Username);

      if (hasLinkedIdentity(destination, providerName, providerSubject)) {
        audit.linkResult = "ALREADY_LINKED";
        writeLog(logger, "info", audit);
        return event;
      }

      await sendCognito(
        client,
        new AdminLinkProviderForUserCommand({
          UserPoolId: event.userPoolId,
          DestinationUser: {
            ProviderName: "Cognito",
            ProviderAttributeValue: destination.Username,
          },
          SourceUser: {
            ProviderName: TRUSTED_PROVIDER,
            ProviderAttributeName: "Cognito_Subject",
            ProviderAttributeValue: providerSubject,
          },
        }),
        "LINK",
      );

      audit.linkResult = "LINKED";
      writeLog(logger, "info", audit);
      return event;
    } catch (error) {
      const safeError = toAccountLinkingError(error);
      audit.providerName ??= providerLogValue(event?.userName);
      audit.linkResult = "REJECTED";
      audit.errorCategory = safeError.category;
      writeLog(logger, "error", audit);
      throw new Error(safeError.publicMessage);
    }
  };
}

async function requireFederatedProfileNotCreated(client, userPoolId, userName) {
  try {
    await client.send(new AdminGetUserCommand({
      UserPoolId: userPoolId,
      Username: userName,
    }));
  } catch (error) {
    if (error?.name === "UserNotFoundException") {
      return;
    }
    throw classifyAwsError(error, "GET_SOURCE");
  }

  throw linkingError(
    "EXISTING_FEDERATED_PROFILE",
    "IDENTITY_CONFLICT",
  );
}

async function listUsersByExactEmail(client, userPoolId, email) {
  const users = [];
  let paginationToken;

  do {
    const response = await sendCognito(
      client,
      new ListUsersCommand({
        UserPoolId: userPoolId,
        Filter: `email = "${email}"`,
        Limit: 60,
        ...(paginationToken ? { PaginationToken: paginationToken } : {}),
      }),
      "LIST",
    );
    if (response?.Users != null && !Array.isArray(response.Users)) {
      throw linkingError("INVALID_AWS_RESPONSE", "SERVICE_FAILURE");
    }
    users.push(...(response?.Users ?? []));
    paginationToken = response?.PaginationToken;
  } while (paginationToken);

  return users;
}

async function getUser(client, userPoolId, username) {
  if (typeof username !== "string" || !username) {
    throw linkingError("INVALID_AWS_RESPONSE", "SERVICE_FAILURE");
  }
  try {
    return await client.send(new AdminGetUserCommand({
      UserPoolId: userPoolId,
      Username: username,
    }));
  } catch (error) {
    throw classifyAwsError(error, "GET_DESTINATION");
  }
}

async function sendCognito(client, command, operation) {
  try {
    return await client.send(command);
  } catch (error) {
    throw classifyAwsError(error, operation);
  }
}

function validateDestination(user, expectedEmail) {
  if (
    typeof user?.Username !== "string"
    || !user.Username
    || isFederatedProfile(user)
  ) {
    throw linkingError("INVALID_LOCAL_USER", "IDENTITY_CONFLICT");
  }

  const attributes = attributesToMap(user.UserAttributes);
  const storedEmail = normalizeEmail(attributes.email);
  if (storedEmail !== expectedEmail || attributes.email?.trim() !== expectedEmail) {
    throw linkingError("LOCAL_EMAIL_MISMATCH", "IDENTITY_CONFLICT");
  }
  if (!isVerified(attributes.email_verified)) {
    throw linkingError("UNVERIFIED_LOCAL_EMAIL", "IDENTITY_CONFLICT");
  }
  if (user.Enabled !== true) {
    throw linkingError("DISABLED_LOCAL_USER", "IDENTITY_CONFLICT");
  }
  if (!USABLE_NATIVE_STATUSES.has(user.UserStatus)) {
    throw linkingError("UNUSABLE_LOCAL_STATUS", "IDENTITY_CONFLICT");
  }
}

function attributesToMap(attributes) {
  if (attributes == null) {
    return {};
  }
  if (!Array.isArray(attributes)) {
    throw linkingError("INVALID_AWS_RESPONSE", "SERVICE_FAILURE");
  }
  return Object.fromEntries(
    attributes
      .filter((attribute) => typeof attribute?.Name === "string")
      .map((attribute) => [attribute.Name, attribute.Value]),
  );
}

function hasLinkedIdentity(user, providerName, providerSubject) {
  const identities = attributesToMap(user?.UserAttributes).identities;
  if (identities == null || identities === "") {
    return false;
  }

  try {
    const parsed = JSON.parse(identities);
    if (!Array.isArray(parsed)) {
      throw new TypeError("identities is not an array");
    }
    return parsed.some(
      (identity) => identity?.providerName === providerName
        && String(identity?.userId) === providerSubject,
    );
  } catch {
    throw linkingError("INVALID_IDENTITIES_ATTRIBUTE", "IDENTITY_CONFLICT");
  }
}

function isFederatedProfile(user) {
  return user?.UserStatus === EXTERNAL_STATUS
    || hasTrustedProviderPrefix(user?.Username);
}

function isValidEmail(email) {
  return email.length <= 254
    && /^[^\s@"\\]+@[^\s@"\\]+\.[^\s@"\\]+$/u.test(email);
}

function classifyAwsError(error, operation) {
  if (error instanceof AccountLinkingError) {
    return error;
  }

  switch (error?.name) {
    case "NotAuthorizedException":
    case "AccessDeniedException":
    case "UnauthorizedException":
      return linkingError(`AWS_${operation}_UNAUTHORIZED`, "CONFIGURATION", error);
    case "AliasExistsException":
    case "InvalidParameterException":
    case "ResourceNotFoundException":
      return linkingError(`AWS_${operation}_CONFLICT`, "IDENTITY_CONFLICT", error);
    case "TooManyRequestsException":
    case "LimitExceededException":
      return linkingError(`AWS_${operation}_THROTTLED`, "SERVICE_FAILURE", error);
    case "InternalErrorException":
    case "ServiceUnavailableException":
      return linkingError(`AWS_${operation}_UNAVAILABLE`, "SERVICE_FAILURE", error);
    default:
      return linkingError(`AWS_${operation}_FAILED`, "SERVICE_FAILURE", error);
  }
}

function toAccountLinkingError(error) {
  return error instanceof AccountLinkingError
    ? error
    : linkingError("UNEXPECTED_FAILURE", "SERVICE_FAILURE", error);
}

function linkingError(category, publicErrorKey, cause = undefined) {
  return new AccountLinkingError(
    category,
    PUBLIC_ERRORS[publicErrorKey],
    cause ? { cause } : undefined,
  );
}

function providerLogValue(userName) {
  return hasTrustedProviderPrefix(userName)
    ? TRUSTED_PROVIDER
    : "UNSUPPORTED";
}

function safeTriggerSource(value) {
  return value === EXTERNAL_TRIGGER ? EXTERNAL_TRIGGER : "OTHER";
}

function safeRequestId(value) {
  return typeof value === "string" && /^[A-Za-z0-9-]{1,128}$/u.test(value)
    ? value
    : undefined;
}

function writeLog(logger, level, audit) {
  const entry = Object.fromEntries(
    Object.entries(audit).filter(([, value]) => value !== undefined),
  );
  const writer = typeof logger?.[level] === "function"
    ? logger[level].bind(logger)
    : logger.log.bind(logger);
  writer(JSON.stringify(entry));
}

const defaultClient = new CognitoIdentityProviderClient({});
export const handler = createHandler({ client: defaultClient });
