const { createBot } = require('mineflayer');

const host = 'localhost';
const port = 25565;
const username = 'TestBot';

let done = false;
let createdLobbyId = null;

const bot = createBot({ host, port, username, version: false });

const finish = (code) => {
  if (done) return;
  done = true;
  setTimeout(() => process.exit(code), 1000);
};

bot.on('login', () => {
  console.log('[bot] logged in');
});

bot.on('spawn', () => {
  console.log('[bot] spawned');
  // Wait a bit for server startup tasks
  setTimeout(() => {
    bot.chat('/games lobby create original 0 80 0 minecraft:world');
  }, 2000);
});

bot.on('message', (json) => {
  const msg = json.toString();
  console.log('[server]', msg);

  if (!createdLobbyId) {
    const match = msg.match(/\(([0-9A-Za-z]+)\)/);
    if (msg.includes('Lobby created successfully') && match) {
      createdLobbyId = match[1];
      setTimeout(() => bot.chat(`/games lobby join ${createdLobbyId}`), 1000);
      setTimeout(() => bot.chat('/games lobby leave'), 5000);
      setTimeout(() => finish(0), 8000);
    }
  }
});

bot.on('kicked', (reason) => {
  console.error('[bot] kicked', reason);
  finish(1);
});

bot.on('error', (err) => {
  console.error('[bot] error', err);
  finish(1);
});

setTimeout(() => {
  console.error('[bot] timeout connecting or no lobby created');
  finish(1);
}, 30000);
