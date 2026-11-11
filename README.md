# PracticeCore - where every file goes

## A) The project (upload to GitHub, it builds PracticeCore.jar)
PracticeCore/
  build.gradle                          <- top folder, next to settings.gradle
  settings.gradle
  README.md
  src/main/resources/plugin.yml
  src/main/resources/config.yml
  src/main/java/com/practiceserver/arenas/   <- only the 17 .java files
      Arena ArenaManager ArenasPlugin BotCleaner CommandHandler DialogMenus
      KitEditor KitManager LayoutManager LobbyListener LobbyManager MenuManager
      RegenManager SessionListener SessionManager SpawnPoint Text (+ .java each)
  server-files/pa_templates.yml          <- NOT for GitHub, see B)
Your .github/workflows/main.yml on GitHub stays as it is.

## B) Files for the SERVER (not GitHub)
- server-files/pa_templates.yml : paste its content under "templates:" in
  plugins/PracticeBot/bot_templates.yml, then /pbadmin reload

## C) Updating an existing server
1. Stop the server. Put the new PracticeCore.jar in /plugins (replace the old one).
2. DELETE plugins/PracticeCore/config.yml so the new default config is created.
   Keep plugins/PracticeCore/arenas.yml.
3. Start. Stand in the lobby and run /parena setlobby
4. You no longer need ItemJoin, DeluxeMenus or Essentials for this plugin.
