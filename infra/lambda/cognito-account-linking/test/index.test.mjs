import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  createHandler,
  parseExternalUsername,
} from "../index.mjs";

const POOL_ID = "ap-southeast-1_example";
const EMAIL = "person@example.com";
const LOCAL_USERNAME = EMAIL;
const GOOGLE_SUBJECT = "google-subject_123";

describe("Cognito account-linking Pre Sign-up handler", () => {
  it("links a first Google login to the one verified native password user", async () => {
    const client = clientFor({
      listedUsers: [localSummary()],
      localUsers: { [LOCAL_USERNAME]: localUser() },
    });
    const event = externalEvent({ email: `  ${EMAIL.toUpperCase()}  ` });

    const result = await handlerFor(client)(event, context());

    assert.equal(result, event);
    const link = call(client, "AdminLinkProviderForUserCommand");
    assert.deepEqual(link.input, {
      UserPoolId: POOL_ID,
      DestinationUser: {
        ProviderName: "Cognito",
        ProviderAttributeValue: LOCAL_USERNAME,
      },
      SourceUser: {
        ProviderName: "Google",
        ProviderAttributeName: "Cognito_Subject",
        ProviderAttributeValue: GOOGLE_SUBJECT,
      },
    });
  });

  for (const userName of [
    `google_${GOOGLE_SUBJECT}`,
    `GOOGLE_${GOOGLE_SUBJECT}`,
  ]) {
    it(`links a trusted provider username with prefix ${userName.slice(0, userName.indexOf("_"))}`, async () => {
      const client = clientFor({
        listedUsers: [localSummary()],
        localUsers: { [LOCAL_USERNAME]: localUser() },
      });

      await handlerFor(client)(externalEvent({ userName }), context());

      assert.deepEqual(call(client, "AdminLinkProviderForUserCommand").input.SourceUser, {
        ProviderName: "Google",
        ProviderAttributeName: "Cognito_Subject",
        ProviderAttributeValue: GOOGLE_SUBJECT,
      });
    });
  }

  it("allows normal Google signup when no native user exists", async () => {
    const client = clientFor({ listedUsers: [] });
    const event = externalEvent();

    assert.equal(await handlerFor(client)(event, context()), event);
    assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 0);
  });

  it("accepts email_verified Boolean true", async () => {
    const client = clientFor({
      listedUsers: [localSummary()],
      localUsers: { [LOCAL_USERNAME]: localUser({ verified: true }) },
    });

    await handlerFor(client)(
      externalEvent({ emailVerified: true }),
      context(),
    );

    assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 1);
  });

  it("accepts email_verified String true case-insensitively", async () => {
    const client = clientFor({
      listedUsers: [localSummary()],
      localUsers: { [LOCAL_USERNAME]: localUser({ verified: "TRUE" }) },
    });

    await handlerFor(client)(
      externalEvent({ emailVerified: "TrUe" }),
      context(),
    );

    assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 1);
  });

  it("rejects an unverified provider email without calling Cognito", async () => {
    const client = clientFor({});

    await assert.rejects(
      handlerFor(client)(
        externalEvent({ emailVerified: false }),
        context(),
      ),
      { message: "A verified provider email is required" },
    );
    assert.equal(client.calls.length, 0);
  });

  it("rejects a missing email without calling Cognito", async () => {
    const client = clientFor({});
    const event = externalEvent();
    delete event.request.userAttributes.email;

    await assert.rejects(
      handlerFor(client)(event, context()),
      { message: "A verified provider email is required" },
    );
    assert.equal(client.calls.length, 0);
  });

  it("rejects an unsupported provider", async () => {
    const client = clientFor({});

    await assert.rejects(
      handlerFor(client)(
        externalEvent({ userName: "Facebook_subject" }),
        context(),
      ),
      { message: "External identity cannot be linked" },
    );
    assert.equal(client.calls.length, 0);
  });

  it("rejects a malformed external username", async () => {
    const client = clientFor({});

    await assert.rejects(
      handlerFor(client)(
        externalEvent({ userName: "Google_" }),
        context(),
      ),
      { message: "External identity cannot be linked" },
    );
    assert.equal(client.calls.length, 0);
  });

  it("fails closed when duplicate native users match the email", async () => {
    const client = clientFor({
      listedUsers: [
        localSummary({ username: LOCAL_USERNAME }),
        localSummary({ username: "person-duplicate" }),
      ],
      localUsers: {
        [LOCAL_USERNAME]: localUser(),
        "person-duplicate": localUser({ username: "person-duplicate" }),
      },
    });

    await assert.rejects(
      handlerFor(client)(externalEvent(), context()),
      { message: "Account linking requires administrator review" },
    );
    assert.equal(calls(client, "AdminGetUserCommand").length, 3);
    assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 0);
  });

  it("excludes an existing federated result from destination selection", async () => {
    const client = clientFor({
      listedUsers: [{
        Username: "Google_another-subject",
        UserStatus: "EXTERNAL_PROVIDER",
      }],
    });
    const event = externalEvent();

    assert.equal(await handlerFor(client)(event, context()), event);
    assert.equal(calls(client, "AdminGetUserCommand").length, 1);
    assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 0);
  });

  for (const userName of ["google_another-subject", "GOOGLE_another-subject"]) {
    it(`recognizes ${userName.slice(0, userName.indexOf("_"))} prefixed users as federated profiles`, async () => {
      const client = clientFor({
        listedUsers: [{
          Username: userName,
          UserStatus: "CONFIRMED",
        }],
      });
      const event = externalEvent();

      assert.equal(await handlerFor(client)(event, context()), event);
      assert.equal(calls(client, "AdminGetUserCommand").length, 1);
      assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 0);
    });
  }

  it("rejects a native destination whose email is not verified", async () => {
    const client = clientFor({
      listedUsers: [localSummary()],
      localUsers: { [LOCAL_USERNAME]: localUser({ verified: false }) },
    });

    await assert.rejects(
      handlerFor(client)(externalEvent(), context()),
      { message: "Account linking requires administrator review" },
    );
    assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 0);
  });

  it("parses trusted provider prefixes case-insensitively and preserves subjects with underscores", () => {
    for (const userName of [
      `Google_${GOOGLE_SUBJECT}`,
      `google_${GOOGLE_SUBJECT}`,
      `GOOGLE_${GOOGLE_SUBJECT}`,
    ]) {
      assert.deepEqual(parseExternalUsername(userName), {
        providerName: "Google",
        providerSubject: GOOGLE_SUBJECT,
      });
    }
  });

  it("rejects unsupported and malformed external provider usernames", () => {
    assert.throws(
      () => parseExternalUsername("Facebook_subject"),
      { name: "AccountLinkingError", category: "UNSUPPORTED_PROVIDER" },
    );
    for (const userName of ["Google_", "Google", "_subject", ""]) {
      assert.throws(
        () => parseExternalUsername(userName),
        { name: "AccountLinkingError", category: "MALFORMED_PROVIDER_USERNAME" },
      );
    }
  });

  it("fails safely when the AWS link call is unauthorized", async () => {
    const sensitiveAwsMessage = `secret failure for ${EMAIL} and ${GOOGLE_SUBJECT}`;
    const client = clientFor({
      listedUsers: [localSummary()],
      localUsers: { [LOCAL_USERNAME]: localUser() },
      linkError: awsError(
        "NotAuthorizedException",
        sensitiveAwsMessage,
      ),
    });
    const messages = [];
    const logger = {
      info: (message) => messages.push(message),
      error: (message) => messages.push(message),
    };

    await assert.rejects(
      handlerFor(client, logger)(externalEvent(), context()),
      { message: "Account linking is not configured" },
    );
    const output = messages.join("\n");
    assert.doesNotMatch(output, /person@example\.com/u);
    assert.doesNotMatch(output, /google-subject_123/u);
    assert.doesNotMatch(output, /secret failure/u);
    assert.match(output, /"errorCategory":"AWS_LINK_UNAUTHORIZED"/u);
  });

  it("does not emit email, subject, token, or complete attributes in logs", async () => {
    const client = clientFor({
      listedUsers: [localSummary()],
      localUsers: { [LOCAL_USERNAME]: localUser() },
    });
    const messages = [];
    const logger = {
      info: (message) => messages.push(message),
      error: (message) => messages.push(message),
    };
    const event = externalEvent();
    event.request.userAttributes.access_token = "highly-sensitive-token";

    await handlerFor(client, logger)(event, context());

    const output = messages.join("\n");
    assert.doesNotMatch(output, /person@example\.com/u);
    assert.doesNotMatch(output, /google-subject_123/u);
    assert.doesNotMatch(output, /Google_google-subject_123/u);
    assert.doesNotMatch(output, /highly-sensitive-token/u);
    assert.doesNotMatch(output, /userAttributes/u);
    assert.match(output, /"emailHash":"[a-f0-9]{16}"/u);
    assert.match(output, /"linkResult":"LINKED"/u);
  });

  it("uses canonical and unsupported provider values in audit logs without raw identity data", async () => {
    for (const userName of ["Google_", "google_", "GOOGLE_"]) {
      const messages = [];
      const logger = {
        info: (message) => messages.push(message),
        error: (message) => messages.push(message),
      };

      await assert.rejects(
        handlerFor(clientFor({}), logger)(
          externalEvent({ userName, email: "private@example.com" }),
          context(),
        ),
        { message: "External identity cannot be linked" },
      );

      const output = messages.join("\n");
      assert.match(output, /"providerName":"Google"/u);
      assert.doesNotMatch(output, new RegExp(userName, "u"));
      assert.doesNotMatch(output, /private@example\.com/u);
    }

    const messages = [];
    const logger = {
      info: (message) => messages.push(message),
      error: (message) => messages.push(message),
    };
    const unsupportedUserName = "Facebook_private-provider-subject";

    await assert.rejects(
      handlerFor(clientFor({}), logger)(
        externalEvent({ userName: unsupportedUserName, email: "private@example.com" }),
        context(),
      ),
      { message: "External identity cannot be linked" },
    );

    const output = messages.join("\n");
    assert.match(output, /"providerName":"UNSUPPORTED"/u);
    assert.doesNotMatch(output, new RegExp(unsupportedUserName, "u"));
    assert.doesNotMatch(output, /private-provider-subject/u);
    assert.doesNotMatch(output, /private@example\.com/u);
  });

  it("requires manual reconciliation when the federated profile already exists", async () => {
    const client = clientFor({ sourceExists: true });

    await assert.rejects(
      handlerFor(client)(externalEvent(), context()),
      { message: "Account linking requires administrator review" },
    );
    assert.equal(calls(client, "ListUsersCommand").length, 0);
    assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 0);
  });

  it("does not relink an identity already present on the native profile", async () => {
    const identities = JSON.stringify([{
      providerName: "Google",
      userId: GOOGLE_SUBJECT,
    }]);
    const client = clientFor({
      listedUsers: [localSummary()],
      localUsers: {
        [LOCAL_USERNAME]: localUser({ identities }),
      },
    });
    const event = externalEvent();

    assert.equal(await handlerFor(client)(event, context()), event);
    assert.equal(calls(client, "AdminLinkProviderForUserCommand").length, 0);
  });

  it("returns non-external trigger events unchanged without AWS calls", async () => {
    const client = clientFor({});
    const event = { triggerSource: "PreSignUp_SignUp" };

    assert.equal(await handlerFor(client)(event, context()), event);
    assert.equal(client.calls.length, 0);
  });
});

function handlerFor(client, logger = silentLogger()) {
  return createHandler({ client, logger });
}

function clientFor({
  listedUsers = [],
  localUsers = {},
  sourceExists = false,
  linkError = undefined,
}) {
  return {
    calls: [],
    async send(command) {
      this.calls.push(command);
      switch (command.constructor.name) {
        case "AdminGetUserCommand":
          if (hasGooglePrefix(command.input.Username)) {
            if (sourceExists) {
              return {
                Username: command.input.Username,
                UserStatus: "EXTERNAL_PROVIDER",
                Enabled: true,
              };
            }
            throw awsError("UserNotFoundException", "source does not exist");
          }
          if (localUsers[command.input.Username]) {
            return localUsers[command.input.Username];
          }
          throw awsError("UserNotFoundException", "local user does not exist");
        case "ListUsersCommand":
          return { Users: listedUsers };
        case "AdminLinkProviderForUserCommand":
          if (linkError) {
            throw linkError;
          }
          return {};
        default:
          throw new Error(`Unexpected command: ${command.constructor.name}`);
      }
    },
  };
}

function externalEvent({
  userName = `Google_${GOOGLE_SUBJECT}`,
  email = EMAIL,
  emailVerified = true,
} = {}) {
  return {
    version: "1",
    triggerSource: "PreSignUp_ExternalProvider",
    region: "ap-southeast-1",
    userPoolId: POOL_ID,
    userName,
    request: {
      userAttributes: {
        email,
        email_verified: emailVerified,
        name: "Example Person",
      },
    },
    response: {},
  };
}

function localSummary({
  username = LOCAL_USERNAME,
  status = "CONFIRMED",
} = {}) {
  return {
    Username: username,
    UserStatus: status,
    Enabled: true,
    Attributes: [
      { Name: "email", Value: EMAIL },
      { Name: "email_verified", Value: "true" },
    ],
  };
}

function localUser({
  username = LOCAL_USERNAME,
  email = EMAIL,
  verified = "true",
  enabled = true,
  status = "CONFIRMED",
  identities = undefined,
} = {}) {
  return {
    Username: username,
    UserStatus: status,
    Enabled: enabled,
    UserAttributes: [
      { Name: "email", Value: email },
      { Name: "email_verified", Value: String(verified) },
      ...(identities === undefined
        ? []
        : [{ Name: "identities", Value: identities }]),
    ],
  };
}

function calls(client, commandName) {
  return client.calls.filter(
    (command) => command.constructor.name === commandName,
  );
}

function call(client, commandName) {
  const matching = calls(client, commandName);
  assert.equal(matching.length, 1);
  return matching[0];
}

function awsError(name, message) {
  const error = new Error(message);
  error.name = name;
  return error;
}

function context() {
  return { awsRequestId: "12345678-abcd-1234-abcd-123456789012" };
}

function hasGooglePrefix(userName) {
  const separator = userName.indexOf("_");
  return separator > 0
    && userName.slice(0, separator).toLowerCase() === "google";
}

function silentLogger() {
  return {
    info() {},
    error() {},
  };
}
