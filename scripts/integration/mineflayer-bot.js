const { createBot } = require('mineflayer');

const host = 'localhost';
const port = 25565;
const username = 'TestBot';

let done = false;

const bot = createBot({ host, port, username, version: false });

const finish = (code) => {
  if (done) return;
  done = true;
  setTimeout(() => process.exit(code), 1000);
};

bot.on('login', () => {
  console.log('[bot] logged in');
  // give server a moment to settle
  setTimeout(() => bot.chat('/games'), 2000);
});

bot.on('spawn', () => {
  console.log('[bot] spawned');
  setTimeout(() => {
    bot.chat('/games lobby create original 0 80 0 world');
    setTimeout(() => bot.chat('/games lobby join latest'), 2000);
    setTimeout(() => bot.chat('/games lobby leave'), 5000);
    setTimeout(() => finish(0), 8000);
  }, 3000);
});

bot.on('message', (json) => {
  const msg = json.toString();
  console.log('[server]', msg);
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
  console.error('[bot] timeout connecting');
  finish(1);
}, 20000);
