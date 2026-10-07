// Keep Next.js development and accept the managed preview host/port flags.
const args = process.argv.slice(2).flatMap((arg) => {
  if (arg === "--strictPort") return [];
  if (arg === "--host") return ["--hostname"];
  if (arg.startsWith("--host=")) return [arg.replace("--host=", "--hostname=")];
  return [arg];
});
process.argv = [process.execPath, "next", "dev", ...args];
await import("next/dist/bin/next");
