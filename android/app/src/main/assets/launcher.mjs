import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath, pathToFileURL } from "node:url";

const appRoot = path.dirname(fileURLToPath(import.meta.url));
const settingsPath = process.argv[2] || path.join(appRoot, "st-settings.json");
process.chdir(appRoot);

const dataRoot = path.join(appRoot, "data");
if (!fs.existsSync(dataRoot)) {
    fs.mkdirSync(dataRoot, { recursive: true });
}

let runtimeSettings = { env: {}, config: {} };
try {
    if (fs.existsSync(settingsPath)) {
        runtimeSettings = JSON.parse(fs.readFileSync(settingsPath, "utf8"));
    }
} catch (error) {
    console.error("Failed to parse settings json:", error);
}

const env = runtimeSettings?.env && typeof runtimeSettings.env === "object" ? runtimeSettings.env : {};
const config = runtimeSettings?.config && typeof runtimeSettings.config === "object" ? runtimeSettings.config : {};

for (const [key, value] of Object.entries(env)) {
    if (typeof key === "string" && key.length > 0 && value !== undefined && value !== null) {
        process.env[key] = String(value);
    }
}

const port = Number(config.port) > 0 ? Number(config.port) : 8000;
const listen = Boolean(config.listen);
const browserLaunchEnabled = Boolean(config.browserLaunchEnabled);
const disableCsrf = Boolean(config.disableCsrf);

process.argv.push("--dataRoot", dataRoot);
process.argv.push("--port", String(port));
process.argv.push("--browserLaunchEnabled", String(browserLaunchEnabled));
process.argv.push("--listen", String(listen));

if (disableCsrf) {
    process.argv.push("--disableCsrf", "true");
}

if (typeof config.requestProxyUrl === "string" && config.requestProxyUrl.length > 0) {
    process.argv.push("--requestProxyEnabled", "true");
    process.argv.push("--requestProxyUrl", config.requestProxyUrl);
}

await import(pathToFileURL(path.join(appRoot, "server.js")).href);
