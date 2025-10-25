import { S3Client, PutObjectCommand } from "@aws-sdk/client-s3";
import { APIGatewayProxyEvent, APIGatewayProxyResult } from "aws-lambda";

const s3 = new S3Client({});
const bucketName = process.env.BUCKET_NAME!;

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    if (!event.body) {
      return { statusCode: 400, body: JSON.stringify({ error: "Missing request body" }) };
    }

    const body = JSON.parse(event.body);
    const { taskId, fileName, fileContent } = body;

    if (!taskId || !fileName || !fileContent) {
      return { statusCode: 400, body: JSON.stringify({ error: "Missing 'taskId', 'fileName' or 'fileContent'" }) };
    }

    const key = `uploads/${taskId}/${fileName}`;
    const buffer = Buffer.from(fileContent, "base64");

    await s3.send(
      new PutObjectCommand({
        Bucket: bucketName,
        Key: key,
        Body: buffer
      })
    );

    return {
      statusCode: 200,
      body: JSON.stringify({ key })
    };
  } catch (err: unknown) {
    console.error("Upload Lambda Error:", err);
    return { statusCode: 500, body: JSON.stringify({ error: (err as Error).message }) };
  }
};
