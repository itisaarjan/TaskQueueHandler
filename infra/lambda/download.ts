import { S3Client, GetObjectCommand } from "@aws-sdk/client-s3";
import { APIGatewayProxyEvent, APIGatewayProxyResult } from "aws-lambda";

const s3 = new S3Client({});

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  const bucketName = process.env.BUCKET_NAME!;
  const key = event.queryStringParameters?.key;

  if (!key) return { statusCode: 400, body: "Missing 'key' parameter" };

  try {
    const data = await s3.send(new GetObjectCommand({ Bucket: bucketName, Key: key }));
    
    // Convert stream to buffer for binary data (images)
    const chunks: Uint8Array[] = [];
    for await (const chunk of data.Body as any) {
      chunks.push(chunk);
    }
    const buffer = Buffer.concat(chunks);

    return { 
      statusCode: 200, 
      body: buffer.toString('base64'),
      headers: {
        'Content-Type': 'text/plain'
      }
    };
  } catch (err: unknown) {
    console.error(err);
    return { statusCode: 500, body: JSON.stringify({ error: (err as Error).message }) };
  }
};
