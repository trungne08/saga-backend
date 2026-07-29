import { createHash } from "node:crypto";
import {
  CognitoIdentityProviderClient,
  ListUsersCommand,
} from "@aws-sdk/client-cognito-identity-provider";

const userPoolId = process.env.COGNITO_USER_POOL_ID?.trim();
if (!userPoolId) {
  console.error("COGNITO_USER_POOL_ID is required");
  process.exitCode = 2;
} else {
  const client = new CognitoIdentityProviderClient({
    ...(process.env.AWS_REGION
      ? { region: process.env.AWS_REGION }
      : {}),
  });
  const users = await listAllUsers(client, userPoolId);
  const report = buildReport(users);
  console.log(JSON.stringify({
    mode: "DRY_RUN",
    userPoolIdHash: hash(userPoolId),
    existingGoogleProfileCount: report.length,
    actionTaken: "NONE",
    profiles: report,
  }, null, 2));
}

async function listAllUsers(client, poolId) {
  const users = [];
  let paginationToken;
  do {
    const response = await client.send(new ListUsersCommand({
      UserPoolId: poolId,
      Limit: 60,
      ...(paginationToken ? { PaginationToken: paginationToken } : {}),
    }));
    users.push(...(response.Users ?? []));
    paginationToken = response.PaginationToken;
  } while (paginationToken);
  return users;
}

function buildReport(users) {
  const nativeUsersByEmail = new Map();
  for (const user of users) {
    if (isGoogleProfile(user) || user.UserStatus === "EXTERNAL_PROVIDER") {
      continue;
    }
    const email = normalizedAttribute(user, "email");
    if (email) {
      const existing = nativeUsersByEmail.get(email) ?? [];
      existing.push(user);
      nativeUsersByEmail.set(email, existing);
    }
  }

  return users
    .filter(isGoogleProfile)
    .map((externalUser) => {
      const email = normalizedAttribute(externalUser, "email");
      const nativeMatches = email ? nativeUsersByEmail.get(email) ?? [] : [];
      return {
        externalProfileHash: hash(externalUser.Username),
        maskedEmail: maskEmail(email),
        matchingNativeProfileHashes: nativeMatches.map(
          (user) => hash(user.Username),
        ),
        disposition: nativeMatches.length > 0
          ? "MANUAL_RECONCILIATION_REQUIRED"
          : "REVIEW_EXTERNAL_ONLY_PROFILE",
      };
    });
}

function isGoogleProfile(user) {
  return typeof user?.Username === "string"
    && user.Username.startsWith("Google_");
}

function normalizedAttribute(user, name) {
  const value = user?.Attributes?.find(
    (attribute) => attribute.Name === name,
  )?.Value;
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function maskEmail(email) {
  const separator = email.indexOf("@");
  if (separator <= 0) {
    return undefined;
  }
  return `${email[0]}***@${email.slice(separator + 1)}`;
}

function hash(value) {
  return createHash("sha256")
    .update(String(value), "utf8")
    .digest("hex")
    .slice(0, 16);
}
