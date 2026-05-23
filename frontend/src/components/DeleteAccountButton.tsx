"use client";

import { useState } from "react";

export function DeleteAccountButton() {
  const [confirming, setConfirming] = useState(false);

  if (!confirming) {
    return (
      <button
        type="button"
        className="bd-btn"
        onClick={() => setConfirming(true)}
        style={{
          borderColor: "#fecaca",
          color: "var(--bad)",
          background: "var(--bg-surface)",
          cursor: "pointer",
        }}
      >
        Delete account
      </button>
    );
  }

  return (
    <form
      action="/auth/delete"
      method="post"
      style={{ display: "flex", gap: 8, alignItems: "center", margin: 0 }}
    >
      <span className="bd-sm" style={{ color: "var(--bad)" }}>
        This permanently deletes your account.
      </span>
      <button
        type="button"
        className="bd-btn"
        onClick={() => setConfirming(false)}
        style={{ cursor: "pointer" }}
      >
        Cancel
      </button>
      <button
        type="submit"
        className="bd-btn"
        style={{
          background: "var(--bad)",
          borderColor: "var(--bad)",
          color: "#fff",
          cursor: "pointer",
        }}
      >
        Yes, delete my account
      </button>
    </form>
  );
}
