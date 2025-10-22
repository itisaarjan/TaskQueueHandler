import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as path from 'path';
import * as S3 from 'aws-cdk-lib/aws-s3';
import * as Lambda from 'aws-cdk-lib/aws-lambda';
import * as Apigw from 'aws-cdk-lib/aws-apigatewayv2';
import * as Integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';

export class InfraStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const s3Bucket = new S3.Bucket(this, 'task-queue-s3-bucket', {
      bucketName: 'task-queue-s3-bucket',
      blockPublicAccess: S3.BlockPublicAccess.BLOCK_ALL,
      encryption: S3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      versioned: true,
      removalPolicy: cdk.RemovalPolicy.DESTROY
    });

    const uploadLambda = new Lambda.Function(this, 'UploadLambda', {
      runtime: Lambda.Runtime.NODEJS_18_X,
      handler: 'upload.handler',
      code: Lambda.Code.fromAsset(path.join(__dirname, '../lambda')),
      environment: { BUCKET_NAME: s3Bucket.bucketName },
      timeout: cdk.Duration.seconds(30),
      memorySize: 256
    });

    const downloadLambda = new Lambda.Function(this, 'DownloadLambda', {
      runtime: Lambda.Runtime.NODEJS_18_X,
      handler: 'download.handler',
      code: Lambda.Code.fromAsset(path.join(__dirname, '../lambda')),
      environment: { BUCKET_NAME: s3Bucket.bucketName },
      timeout: cdk.Duration.seconds(30),
      memorySize: 256
    });

    s3Bucket.grantPut(uploadLambda);
    s3Bucket.grantRead(downloadLambda);

    const httpApi = new Apigw.HttpApi(this, 'TaskQueueApi', {
      apiName: 'task-queue-api',
      corsPreflight: {
        allowHeaders: ['Content-Type', 'Authorization'],
        allowMethods: [
          Apigw.CorsHttpMethod.GET,
          Apigw.CorsHttpMethod.POST,
          Apigw.CorsHttpMethod.OPTIONS
        ],
        allowOrigins: ['*']
      }
    });

    const uploadIntegration = new Integrations.HttpLambdaIntegration(
      'UploadIntegration',
      uploadLambda
    );

    const downloadIntegration = new Integrations.HttpLambdaIntegration(
      'DownloadIntegration',
      downloadLambda
    );

    httpApi.addRoutes({
      path: '/upload',
      methods: [Apigw.HttpMethod.POST],
      integration: uploadIntegration
    });

    httpApi.addRoutes({
      path: '/download',
      methods: [Apigw.HttpMethod.GET],
      integration: downloadIntegration
    });

    new cdk.CfnOutput(this, 'ApiUrl', {
      value: httpApi.apiEndpoint,
      description: 'Base URL of the Task Queue API Gateway'
    });
  }
}
