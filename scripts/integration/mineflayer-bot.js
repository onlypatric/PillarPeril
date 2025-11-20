const { createBot } = require('mineflayer');

const host = 'localhost';
const port = 25565;
const botNames = ['TestBot', 'TestBot2', 'TestBot3'];

let done = false;
let lobbyId = null;
const joinerStatus = {};

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

const createJoiner = (name, idx, delay) => {
  const bot = createBot({ host, port, username: name, version: false });
  handleCommonEvents(bot, `joiner${idx + 1}`);
  bot.on('login', () => console.log(`[joiner${idx + 1}] logged in`));
  bot.once('spawn', () => {
    setTimeout(() => {
      if (lobbyId) {
        bot.chat(`/games lobby join ${lobbyId}`);
        joinerStatus[name] = 'joined';
      }
    }, 500);
  });
  return bot;
};

const bootstrap = () => {
  const creator = createBot({ host, port, username: botNames[0], version: false });
  handleCommonEvents(creator, 'creator');

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

    // Treat obvious server errors as failures
    if (/exception|error/i.test(msg) && !msg.includes('Lobby error')) {
      console.error('[bot] detected server error in chat');
      finish(1);
      return;
    }

    if (!lobbyId) {
      const match = msg.match(/\(([0-9A-Za-z]+)\)/);
      if (msg.includes('Lobby created successfully') && match) {
        lobbyId = match[1];
        console.log('[bot] captured lobby id', lobbyId);
        // creator joins first
        setTimeout(() => creator.chat(`/games lobby join ${lobbyId}`), 500);
        // spawn joiners staggered to avoid throttle
        botNames.slice(1).forEach((name, idx) => {
          setTimeout(() => createJoiner(name, idx, idx * 800), 1200 + idx * 1200);
        });
        // start games and simulate leave/rejoin
        setTimeout(() => creator.chat(`/games lobby force-start ${lobbyId}`), 6000);
        setTimeout(() => creator.chat('/games lobby leave'), 11000);
        setTimeout(() => creator.chat(`/games lobby join ${lobbyId}`), 13000);
        setTimeout(() => creator.chat(`/games lobby force-start ${lobbyId}`), 17000);
        setTimeout(() => finish(0), 25000);
      }
    }
  });

  setTimeout(() => {
    console.error('[bot] timeout during integration scenario');
    finish(1);
  }, 45000);
};

bootstrap();
