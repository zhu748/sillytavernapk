import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath, pathToFileURL } from "node:url";

const appRoot = path.dirname(fileURLToPath(import.meta.url));
process.chdir(appRoot);

const dataRoot = path.join(appRoot, "data");
if (!fs.existsSync(dataRoot)) {
    fs.mkdirSync(dataRoot, { recursive: true });
}

process.argv.push("--dataRoot", dataRoot);
process.argv.push("--port", "8000");
process.argv.push("--browserLaunchEnabled", "false");
process.argv.push("--listen", "false");

await import(pathToFileURL(path.join(appRoot, "server.js")).href);
