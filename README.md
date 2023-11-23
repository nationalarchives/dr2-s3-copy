# S3 Copy

Due to the limitations of step functions, we can't do a multipart upload using native step function actions.
This will cause problems later because the standard copy option can only copy files up to 5Gb so we need this custom lambda to do the copy operation.

Currently, we're using static credentials to access the bucket for Preservica. 
This lambda wil stream the file from our staging cache bucket using its own IAM role. 
It will then get the credentials from Systems Manager for the Preservica user which will allow us to stream the file to their bucket.

When we have a role set up which will allow access to the Preservica bucket, this lambda can be changed to use the copy command.

[Link to the infrastructure code](https://github.com/nationalarchives/dr2-terraform-environments)

## Environment Variables

| Name        | Description                              |
|-------------|------------------------------------------|
| ENVIRONMENT | The environment the lambda is running in |

## Example input
```json
{
  "BatchInput": {
    "tnaBucket": "<environment>-dr2-ingest-staging-cache",
    "preservicaBucket": "preservica-bucket"
  },
  "Items": [
    {
      "Key": "opex/executionId/file",
      "Size": 1
    }
  ]
}
```
