const { createBot } = require('mineflayer');

const host = 'localhost';
const port = 25565;

const botNames = ['TestBot', 'TestBot2', 'TestBot3'];
let done = false;
let lobbyId = null;

const finish = (code) => {
  if (done) return;
  done = true;
  setTimeout(() => process.exit(code), 1000);
};

const handleCommonEvents = (bot, label) => {
  bot.on('error', (err) => {
    console.error(`[${label}] error`, err);
    finish(1);
  });
  bot.on('kicked', (reason) => {
    console.error(`[${label}] kicked`, reason);
    finish(1);
  });
};

const bootstrap = async () => {
  const creator = createBot({ host, port, username: botNames[0], version: false });
  const joiners = botNames.slice(1).map((name) => createBot({ host, port, username: name, version: false }));

  handleCommonEvents(creator, 'creator');
  joiners.forEach((b, i) => handleCommonEvents(b, `joiner${i + 1}`));

  creator.on('login', () => console.log('[creator] logged in'));
  creator.on('spawn', () => {
    console.log('[creator] spawned');
    setTimeout(() => {
      const { x, y, z } = creator.entity.position;
      const worldName = 'minecraft:overworld';
      creator.chat(`/games lobby create original ${Math.floor(x)} ${Math.floor(y)} ${Math.floor(z)} ${worldName}`);
    }, 2000);
  });

  creator.on('message', (json) => {
    const msg = json.toString();
    console.log('[server]', msg);
    if (!lobbyId) {
      const match = msg.match(/\(([0-9A-Za-z]+)\)/);
      if (msg.includes('Lobby created successfully') && match) {
        lobbyId = match[1];
        setTimeout(() => creator.chat(`/games lobby join ${lobbyId}`), 500);
        setTimeout(() => joiners.forEach((b) => b.chat(`/games lobby join ${lobbyId}`)), 1000);
        setTimeout(() => creator.chat(`/games lobby force-start ${lobbyId}`), 5000);
        setTimeout(() => joiners[0]?.end('leaving'), 8000);
        setTimeout(() => creator.chat('/games lobby leave'), 11000);
        setTimeout(() => creator.chat(`/games lobby join ${lobbyId}`), 13000);
        setTimeout(() => creator.chat(`/games lobby force-start ${lobbyId}`), 15000);
        setTimeout(() => finish(0), 20000);
      }
    }
  });

  setTimeout(() => {
    console.error('[bot] timeout during integration scenario');
    finish(1);
  }, 30000);
};

bootstrap();
