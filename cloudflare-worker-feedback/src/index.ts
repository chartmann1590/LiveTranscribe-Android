/**
 * LiveTranscribe GitHub feedback relay. Separate Worker from cloudflare-worker/ (the
 * Stripe entitlement backend) so GitHub and Stripe secrets never share a deployment.
 *
 * The Android app's "Report a Problem" feature used to embed a GitHub personal access
 * token directly in the APK (BuildConfig.GITHUB_API_TOKEN, sent client-side as a Bearer
 * header to api.github.com, with certificate pinning to GitHub's cert) so it could
 * create/read issues and comments, and push screenshots via the Contents API. That token
 * has issue-write and repo-content-write scope — extractable from any downloaded APK in
 * seconds (`strings` on the dex finds any literal compiled into the app), handing every
 * installer of the app write access to this repository. It has been shipping in every CI
 * release build.
 *
 * Moved here instead: the token lives only as a Worker secret (`wrangler secret put
 * GITHUB_TOKEN`), and the client sends plain issue/comment content with no credential
 * attached.
 *
 * Trade-off worth knowing: these endpoints are unauthenticated (a mobile client has no
 * secret it could present that isn't just as extractable as the PAT this replaces).
 * That's an open write surface for issue/comment spam and pushes to GITHUB_ASSETS_DIR —
 * much lower severity than a leaked repo-write credential (spam is noise you can
 * lock/delete; a leaked PAT is a compromised repository), but real. If it becomes a
 * problem, add a Cloudflare Rate Limiting rule on this route from the dashboard — no
 * code change needed.
 */

interface Env {
  GITHUB_TOKEN: string;
}

const GITHUB_OWNER = 'chartmann1590';
const GITHUB_REPO = 'LiveTranscribe-Android';
const GITHUB_ASSETS_DIR = 'feedback-assets';
const GITHUB_API = 'https://api.github.com';
const MAX_TITLE_LENGTH = 200;
const MAX_BODY_LENGTH = 8_000;
// ~5 MB of raw image bytes, base64-encoded (base64 runs ~1.33x the raw size).
const MAX_IMAGE_BASE64_LENGTH = 7_000_000;

interface CreateIssueRequest {
  title: string;
  body: string;
}

interface PostCommentRequest {
  body: string;
}

interface UploadImageRequest {
  filename: string;
  contentBase64: string;
}

function githubHeaders(env: Env): HeadersInit {
  return {
    Authorization: `Bearer ${env.GITHUB_TOKEN}`,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'livetranscribe-cloudflare-worker',
    'Content-Type': 'application/json',
  };
}

function corsHeaders(): HeadersInit {
  return {
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Max-Age': '86400',
  };
}

function errorResponse(status: number, message: string): Response {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { 'Content-Type': 'application/json', ...corsHeaders() },
  });
}

async function proxyGithub(path: string, env: Env, init?: RequestInit): Promise<Response> {
  try {
    const ghResponse = await fetch(`${GITHUB_API}${path}`, {
      ...init,
      headers: { ...githubHeaders(env), ...(init?.headers ?? {}) },
    });
    return new Response(ghResponse.body, {
      status: ghResponse.status,
      headers: {
        'Content-Type': ghResponse.headers.get('Content-Type') ?? 'application/json',
        'Cache-Control': 'no-store',
        ...corsHeaders(),
      },
    });
  } catch (err) {
    console.log(JSON.stringify({ event: 'github_fetch_failed', path, errorType: err instanceof Error ? err.name : 'unknown' }));
    return errorResponse(502, 'GitHub is unavailable');
  }
}

function validateCreateIssueRequest(body: unknown): CreateIssueRequest | null {
  if (typeof body !== 'object' || body === null) return null;
  const title = (body as { title?: unknown }).title;
  const text = (body as { body?: unknown }).body;
  if (typeof title !== 'string' || typeof text !== 'string') return null;
  const trimmedTitle = title.trim();
  if (trimmedTitle.length === 0 || trimmedTitle.length > MAX_TITLE_LENGTH) return null;
  if (text.length === 0 || text.length > MAX_BODY_LENGTH) return null;
  return { title: trimmedTitle, body: text };
}

function validatePostCommentRequest(body: unknown): PostCommentRequest | null {
  if (typeof body !== 'object' || body === null) return null;
  const text = (body as { body?: unknown }).body;
  if (typeof text !== 'string' || text.length === 0 || text.length > MAX_BODY_LENGTH) return null;
  return { body: text };
}

function validateUploadImageRequest(body: unknown): UploadImageRequest | null {
  if (typeof body !== 'object' || body === null) return null;
  const filename = (body as { filename?: unknown }).filename;
  const contentBase64 = (body as { contentBase64?: unknown }).contentBase64;
  if (typeof filename !== 'string' || typeof contentBase64 !== 'string') return null;
  // Basename only — no path separators, so the client can never write outside
  // GITHUB_ASSETS_DIR regardless of what it sends.
  if (!/^[a-zA-Z0-9_.-]{1,120}$/.test(filename)) return null;
  if (contentBase64.length === 0 || contentBase64.length > MAX_IMAGE_BASE64_LENGTH) return null;
  return { filename, contentBase64 };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    if (url.pathname === '/issue' && request.method === 'POST') {
      let issueReq: CreateIssueRequest | null = null;
      try {
        issueReq = validateCreateIssueRequest(await request.json());
      } catch {
        issueReq = null;
      }
      if (!issueReq) return errorResponse(400, 'expected {title: string, body: string}');
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues`, env, {
        method: 'POST',
        body: JSON.stringify(issueReq),
      });
    }

    const issueMatch = url.pathname.match(/^\/issue\/(\d+)$/);
    if (issueMatch && request.method === 'GET') {
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${issueMatch[1]}`, env);
    }

    const commentsMatch = url.pathname.match(/^\/issue\/(\d+)\/comments$/);
    if (commentsMatch && request.method === 'GET') {
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${commentsMatch[1]}/comments`, env);
    }
    if (commentsMatch && request.method === 'POST') {
      let commentReq: PostCommentRequest | null = null;
      try {
        commentReq = validatePostCommentRequest(await request.json());
      } catch {
        commentReq = null;
      }
      if (!commentReq) return errorResponse(400, 'expected {body: string}');
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/issues/${commentsMatch[1]}/comments`, env, {
        method: 'POST',
        body: JSON.stringify(commentReq),
      });
    }

    if (url.pathname === '/upload-image' && request.method === 'POST') {
      let uploadReq: UploadImageRequest | null = null;
      try {
        uploadReq = validateUploadImageRequest(await request.json());
      } catch {
        uploadReq = null;
      }
      if (!uploadReq) return errorResponse(400, 'expected {filename: string, contentBase64: string}');
      const path = `${GITHUB_ASSETS_DIR}/${uploadReq.filename}`;
      return proxyGithub(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/contents/${path}`, env, {
        method: 'PUT',
        body: JSON.stringify({ message: `Upload ${uploadReq.filename}`, content: uploadReq.contentBase64 }),
      });
    }

    return errorResponse(404, 'not found');
  },
} satisfies ExportedHandler<Env>;
