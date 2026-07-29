# SAGA Cognito account linking

This directory contains a standalone Node.js 20 AWS Lambda for the Cognito
**Pre sign-up** trigger. It does not replace the existing Pre Token Generation
role Lambda and is not loaded by the Spring Boot application.

## Repository and User Pool findings

The repository contains only the User Pool issuer URI, app-client ID, and
app-client secret configuration. It contains no exported User Pool definition,
CloudFormation, CDK, Terraform, SAM, or `DescribeUserPool` output. Consequently,
the repository **does not prove** whether email is configured as a User Pool
sign-in identifier or alias.

That setting is immutable after User Pool creation. Neither this Lambda nor the
Spring Boot backend can change it. Check the deployed pool before creating
another native user:

```bash
aws cognito-idp describe-user-pool \
  --user-pool-id USER_POOL_ID \
  --query 'UserPool.{UsernameAttributes:UsernameAttributes,AliasAttributes:AliasAttributes}'
```

- `UsernameAttributes` containing `email`, or `AliasAttributes` containing
  `email`, proves that email is supported for sign-in.
- If neither contains `email`, treat the pool as username-based. Every new
  native user must be created with the normalized (trimmed, lowercase) email as
  its Cognito `Username`. Do not accept a separate arbitrary username.

No native-user creation endpoint or Cognito administrative user service exists
in this repository. Any external provisioning workflow must enforce the rule
above. It must keep passwords entirely in Cognito and must not mark email as
verified until verification has actually occurred. The existing Spring backend
already rejects normal application access unless the OIDC `email_verified`
claim is true.

## Linking decision flow

The handler performs this flow:

1. Return all triggers except `PreSignUp_ExternalProvider` unchanged.
2. Split the federated username on its first underscore. Accept only
   `Google_<subject>`.
3. normalize the provider email with `trim().toLowerCase()`, validate its
   format, and require `email_verified` to be Boolean `true` or String `"true"`.
4. Use `AdminGetUser` to ensure `Google_<subject>` is not already an independent
   federated profile. If it is, stop and require manual reconciliation.
5. Call `ListUsers` with the exact filter `email = "<normalized-email>"`.
6. Exclude `Google_` usernames and `EXTERNAL_PROVIDER` statuses. Zero native
   users allows Cognito's normal Google first-sign-in flow; more than one fails
   closed.
7. Validate the sole candidate with `AdminGetUser`: exact lowercase email,
   verified email, enabled account, and a usable native status are required.
8. If the destination's `identities` attribute already records the same Google
   subject, return successfully without relinking.
9. Call `AdminLinkProviderForUser` with the native username as the Cognito
   destination and Google's subject as `Cognito_Subject`.

The function never links by provider email, deletes a user, changes database
identifiers, logs a complete event, or assigns application roles.

## Install, test, and package

From this directory:

```bash
npm ci
npm test
npm ci --omit=dev
zip -r cognito-account-linking.zip index.mjs package.json node_modules
```

PowerShell packaging equivalent:

```powershell
npm ci
npm test
npm ci --omit=dev
Compress-Archive -Force `
  -Path index.mjs,package.json,node_modules `
  -DestinationPath cognito-account-linking.zip
```

Deploy a new function (replace every placeholder):

```bash
aws lambda create-function \
  --function-name saga-cognito-account-linking \
  --runtime nodejs20.x \
  --architectures arm64 \
  --handler index.handler \
  --role arn:aws:iam::ACCOUNT_ID:role/SAGA_ACCOUNT_LINKING_ROLE \
  --zip-file fileb://cognito-account-linking.zip \
  --timeout 10 \
  --memory-size 256
```

For a reviewed update to an existing function:

```bash
aws lambda update-function-code \
  --function-name saga-cognito-account-linking \
  --zip-file fileb://cognito-account-linking.zip \
  --publish
```

Prefer the repository's approved infrastructure workflow if one is added
later. These commands are documentation only; this change does not deploy or
mutate AWS.

## IAM

Attach
[`iam-policy.example.json`](./iam-policy.example.json) to the Lambda execution
role after replacing `REGION`, `ACCOUNT_ID`, and `USER_POOL_ID`. Its Cognito
permissions are limited to:

- `cognito-idp:ListUsers`
- `cognito-idp:AdminGetUser`
- `cognito-idp:AdminLinkProviderForUser`

The policy resource must be the exact deployed User Pool ARN. Do not replace it
with `"*"`. The role also needs the usual CloudWatch Logs permissions, normally
provided by the AWS-managed `AWSLambdaBasicExecutionRole`; those permissions
are unrelated to Cognito and are intentionally not in the example policy.

## Cognito trigger configuration

In the Cognito console:

1. Open the exact SAGA User Pool.
2. Open **Extensions** / **Lambda triggers**.
3. Add `saga-cognito-account-linking` to **Pre sign-up** for the
   **Pre sign-up** event.
4. Leave the existing role function attached to **Pre token generation**.
5. Confirm Cognito has permission to invoke the Lambda.

There must be only one account-linking implementation, and it must run before
the first Google sign-in creates an external profile. Do not duplicate this
logic in the Spring login-success handler.

## Google attribute mappings

In the User Pool's Google identity-provider configuration, map at least:

| Google attribute | User Pool attribute |
| --- | --- |
| `email` | `email` |
| `email_verified` | `email_verified` |
| `name` | `name` |

`email` and `email_verified` must be present in the Pre sign-up event. The app
client needs `email`, `openid`, and `profile`, as already configured in Spring.
Google client credentials remain in Cognito, not in Spring or this Lambda.

## Existing Google profiles: dry-run only

`AdminLinkProviderForUser` must run before Cognito creates an independent
`Google_<subject>` user. This Lambda deliberately stops if that profile already
exists. It never deletes the profile and never rewrites a MySQL `cognitoSub`.

Generate a read-only, privacy-reduced inventory:

```bash
export AWS_REGION=ap-southeast-1
export COGNITO_USER_POOL_ID=USER_POOL_ID
npm run report:existing
```

PowerShell:

```powershell
$env:AWS_REGION = "ap-southeast-1"
$env:COGNITO_USER_POOL_ID = "USER_POOL_ID"
npm run report:existing
```

The report calls only `ListUsers`, hashes usernames, masks emails, and writes
`actionTaken: "NONE"`. Every `MANUAL_RECONCILIATION_REQUIRED` result needs a
human-reviewed migration plan that identifies the canonical native profile,
maps dependent MySQL records by current immutable `cognitoSub`, backs up data,
and separately approves any destructive Cognito or database operation. Do not
run a bulk delete or silently replace database subjects.

## Manual end-to-end verification

Use a non-production pool first:

1. Confirm the pool's `UsernameAttributes` / `AliasAttributes` setting with
   `DescribeUserPool`.
2. Create and verify a native user. In a username-based pool, use the normalized
   lowercase email as `Username`.
3. Set a Cognito password without sending it through or storing it in SAGA.
4. Sign in with the native email/password and record the ID token's `sub`.
5. Sign out, then choose **Continue with Google** for the exact same verified
   email.
6. Confirm the Lambda log records `linkResult: "LINKED"` and no sensitive
   attributes.
7. Confirm the second ID token has the same `sub` as step 4 and that `/api/auth/me`
   resolves the same local profile.
8. Sign out and repeat both login methods. Confirm both continue to use the
   canonical `sub`.
9. Inspect the User Pool: the native profile should contain the Google identity,
   and no independent `Google_<subject>` profile should have been created.
10. Test a Google account with no native match; Cognito should create its normal
    external profile.
11. Test unverified, duplicate, disabled, and pre-existing-profile cases in a
    controlled pool; each must fail without linking.

The CloudWatch log group is:

```text
/aws/lambda/saga-cognito-account-linking
```

Logs contain only trigger category, trusted provider category, hashed email and
destination identifiers, link result, invocation request ID, and safe error
category.

## Rollback

1. Remove only the **Pre sign-up** association for this Lambda, or point its
   alias back to a previously approved version.
2. Leave the Pre Token Generation role Lambda attached.
3. Keep the Lambda and CloudWatch logs during the investigation.
4. Verify Google first-sign-in behavior in a test account.

Rollback does not undo identities already linked successfully. Reversing a
link or reconciling an existing `Google_<subject>` profile is a separate,
explicitly approved migration; it must not be automated by this function.
