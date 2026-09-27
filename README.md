# BXLogin

BXLogin is a customized fork of MazeAuth adapted for a Turkish Minecraft server.

Features:
- `/kayıt <şifre> <şifre-tekrar>`
- `/giriş <şifre>`
- `/login <şifre>`
- `/register <şifre> <şifre-tekrar>`
- `/şifre değiştir <yeni-şifre> <tekrar>`
- 3 minute authentication deadline
- BossBar countdown showing the remaining time
- Inventory, movement, interaction, chat and non-auth commands blocked before login
- SQLite by default, with MySQL/MariaDB/Redis storage inherited from the base project
- Turkish messages in `messages/messages.yml`
- `bxlogin-icon.png` included as the project/plugin artwork

The original project's MIT license is retained in `LICENSE`. This project is a modified version of that MIT-licensed code; keep the original copyright and license notice when redistributing.
