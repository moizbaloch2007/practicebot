# PracticeCore

Hub menu -> mode -> difficulty -> arena -> prep time (set your hotbar) -> /fight -> countdown -> bot.
Kit editor with Extra Materials. Commands: /pc, /practicecore, /leave, /fight, /parena

## Build the jar (no installs, uses GitHub)
1. Open your GitHub repository, replace the files with the ones in this folder
   (Add file -> Upload files; the .github/workflows/main.yml you made earlier can stay).
2. Wait ~2 minutes in the Actions tab for the green check.
3. Download the "PracticeCore" artifact, unzip -> PracticeCore.jar
4. Delete the old PracticeArenas.jar from /plugins, add PracticeCore.jar, restart.
5. Copy plugins/PracticeArenas/arenas.yml to plugins/PracticeCore/arenas.yml (keeps your arenas).

## Bot templates
Paste pa_templates.yml under "templates:" in plugins/PracticeBot/bot_templates.yml, then /pbadmin reload.
