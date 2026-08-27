const { execSync } = require('child_process');
////
// ANSI Color Codes
const cyan = '\x1b[36m';
const green = '\x1b[32m';
const yellow = '\x1b[33m';
const magenta = '\x1b[35m';
const blue = '\x1b[34m';
const gray = '\x1b[90m';
const reset = '\x1b[0m';
const bold = '\x1b[1m';

console.clear();
console.log(`${cyan}${bold}🚀 STARTING 01E-COM MICROSERVICES...${reset}\n`);

try {
  // Check if Docker is installed
  // Run docker compose up -d
  execSync('docker compose up -d', { stdio: 'inherit' });
} catch (error) {
  console.error('\nFailed to start docker containers.');
  process.exit(1);
}

console.log(`\n${cyan}========================================================================================${reset}`);
console.log(`${bold}${yellow}                       🚀 01E-COM MICROSERVICES RUNNING ENDPOINTS                       ${reset}`);
console.log(`${cyan}========================================================================================${reset}`);

const services = [
  { name: 'Frontend (Angular)', status: '● Running', port: '8443 / 8088', url: 'https://localhost:8443 - http://localhost:8088', color: green },
  { name: 'Jenkins',             status: '● Running', port: '8090',        url: 'http://localhost:8090', color: magenta },
  { name: 'Spring Gateway',     status: '● Running', port: '8089',        url: 'https://localhost:8089', color: magenta },
  { name: 'User Service',       status: '● Running', port: '8081',        url: 'http://user-service:8081', color: yellow },
  { name: 'Product Service',    status: '● Running', port: '8082',        url: 'http://product-service:8082', color: yellow },
  { name: 'Media Service',      status: '● Running', port: '8083',        url: 'http://media-service:8083', color: yellow },
  { name: 'MongoDB',            status: '● Running', port: '27017',       url: 'mongodb://mongodb:27017', color: blue },
  { name: 'Redis',              status: '● Running', port: '6379',        url: 'redis://redis:6379', color: blue },
  { name: 'Apache Kafka',       status: '● Running', port: '9092',        url: 'kafka:9092', color: gray },
];

// Header
console.log(`${bold}SERVICE              | STATUS    | INTERNAL PORT | ENDPOINT${reset}`);
console.log(`${gray}----------------------------------------------------------------------------------------${reset}`);

// Rows
services.forEach(s => {
  const name = s.name.padEnd(20);
  const status = s.status.padEnd(9);
  const port = s.port.padEnd(13);
  console.log(`${s.color}${name}${reset} | ${green}${status}${reset} | ${port} | ${cyan}${s.url}${reset}`);
});

console.log(`${cyan}========================================================================================${reset}`);
console.log(`${gray} Run "docker compose down" to stop all containers.\n${reset}`);
