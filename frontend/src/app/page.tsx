import { redirect } from "next/navigation";
import { getAuthedUser } from "@/lib/auth";

export default async function Home() {
  const user = await getAuthedUser();
  redirect(user ? "/dashboard" : "/login");
}
