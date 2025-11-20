const { createBot } = require('mineflayer');

const host = 'localhost';
const port = 25565;
const botNames = ['TestBot', 'TestBot2', 'TestBot3'];

let done = false;
let lobbyId = null;
const joinerStatus = {};
const startedAt = Date.now();

const ts = () => new Date().toISOString();
const logAction = (label, msg) => console.log(`[${ts()}][${label}] ${msg}`);

const finish = (code) => {
  if (done) return;
  done = true;
  logAction('bot', `Finishing with code ${code} after ${(Date.now() - startedAt) / 1000}s`);
  setTimeout(() => process.exit(code), 1000);
};

const handleCommonEvents = (bot, label) => {
  bot.on('error', (err) => {
    console.error(`[${ts()}][${label}] error`, err);
    finish(1);
  });
  bot.on('kicked', (reason) => {
    console.error(`[${ts()}][${label}] kicked`, reason);
    finish(1);
  });
};

const createJoiner = (name, idx, delay) => {
  const bot = createBot({ host, port, username: name, version: false });
  handleCommonEvents(bot, `joiner${idx + 1}`);
  bot.on('login', () => logAction(`joiner${idx + 1}`, 'logged in'));
  bot.once('spawn', () => {
    logAction(`joiner${idx + 1}`, 'spawned');
    setTimeout(() => {
      if (lobbyId) {
        logAction(`joiner${idx + 1}`, `chat /games lobby join ${lobbyId}`);
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

  creator.on('login', () => logAction('creator', 'logged in'));
  creator.on('spawn', () => {
    logAction('creator', 'spawned');
    setTimeout(() => {
      const { x, y, z } = creator.entity.position;
      const worldName = 'minecraft:overworld';
      const cmd = `/games lobby create original ${Math.floor(x)} ${Math.floor(y)} ${Math.floor(z)} ${worldName}`;
      logAction('creator', `chat ${cmd}`);
      creator.chat(cmd);
    }, 2000);
  });

  creator.on('message', (json) => {
    const msg = json.toString();
    console.log(`[${ts()}][server] ${msg}`);

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
        logAction('bot', `captured lobby id ${lobbyId}`);
        // creator joins first
        setTimeout(() => {
          logAction('creator', `chat /games lobby join ${lobbyId}`);
          creator.chat(`/games lobby join ${lobbyId}`);
        }, 500);
        // spawn joiners staggered to avoid throttle
        botNames.slice(1).forEach((name, idx) => {
          setTimeout(() => createJoiner(name, idx, idx * 800), 1200 + idx * 1200);
        });
        // start one game, then eliminate joiners so creator wins
        setTimeout(() => { logAction('creator', `chat /games lobby force-start ${lobbyId}`); creator.chat(`/games lobby force-start ${lobbyId}`); }, 6000);
        // give a moment for game to start, then kill other bots to trigger win
        setTimeout(() => { logAction('creator', 'chat /kill TestBot2'); creator.chat('/kill TestBot2'); }, 12000);
        setTimeout(() => { logAction('creator', 'chat /kill TestBot3'); creator.chat('/kill TestBot3'); }, 14000);
        // show info/leaderboard after win should be recorded
        setTimeout(() => { logAction('creator', 'chat /games leaderboard'); creator.chat('/games leaderboard'); }, 18000);
        setTimeout(() => { logAction('creator', 'chat /games info'); creator.chat('/games info'); }, 20000);
        setTimeout(() => finish(0), 24000);
      }
    }
  });

  setTimeout(() => {
    console.error('[bot] timeout during integration scenario');
    finish(1);
  }, 30000);
};

bootstrap();
