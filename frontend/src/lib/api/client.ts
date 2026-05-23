import {
  AuthApi,
  Configuration,
  PacketsApi,
  SearchApi,
  SessionsApi,
  UsersApi,
} from "@/openapi";

export interface ApiClient {
  auth: AuthApi;
  packets: PacketsApi;
  search: SearchApi;
  sessions: SessionsApi;
  users: UsersApi;
}

function apiBaseUrl(): string {
  return (process.env.API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
}

export function apiClient(accessToken?: string): ApiClient {
  const configuration = new Configuration({
    basePath: apiBaseUrl(),
    accessToken,
  });
  return {
    auth: new AuthApi(configuration),
    packets: new PacketsApi(configuration),
    search: new SearchApi(configuration),
    sessions: new SessionsApi(configuration),
    users: new UsersApi(configuration),
  };
}
