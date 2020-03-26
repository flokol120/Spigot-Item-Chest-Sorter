# Spigot-Item-Chest-Sorter
A spigot minecraft plugin to sort your messy items into separate chests

## How can I build a jar using IntelliJ idea which I can use on my server?
### 1. Setup an artifact
1. Open the Project Structure using File --> Project Structure (or STRG + ALT + SHIGT + S)
2. Navigate to Arifacts
3. Add a new artifact using the '+' symbol
4. Select JAR --> From modules with dependencies...
5. Select the Item_Chest_Sorter.main module in the drop-down
6. Click 'ok'
7. Click the '+' symbol right under 'Output Layout'
8. Select 'File'
9. Select both the config.yml and the plugin.yml
If you like to you can change the output directory and the name (I configured it to put the jar directly into my servers plugin folder).
### 2. Build an artifact
0. Close the Prject Structure window, if still opened 
1. Select Build --> Build Artifacts...
2. In the dialog select your created artifact

