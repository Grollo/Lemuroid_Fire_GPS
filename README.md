# Lemuroid Fire GPS

This is a branch off [Lemuroid](https://github.com/Swordfish90/Lemuroid) made to run Pokémon Fire GPS, which is a ROM hack of Pokémon Fire Red. With location permission, it reads your gps position to gate progress in the game. What real world location each in-game area corresponds to can be customized via the location file.

## Installation
1. Download the [APK file](https://github.com/Grollo/Lemuroid_Fire_GPS/releases/latest/download/Lemuroid_Fire_GPS.apk) and install it on your phone (Android only).
2. Create a folder on your phone for rom files, and select it as rom folder within the app.
3. Create/acquire the ROM hack and location file (see below)
4. Place the romhack (pokefirered.gba) and a location file (pokefirered.csv) in that folder. 
5. Start up the game, enable location permission
6. Start playing and get moving!

## ROM file
For legal reasons I'm not able to host or link to the complete gba file here. [Here](rom-patch/pokefirered.bps) is a patch file, which you can combine with a standard Pokémon Fire Red .gba file on a site such as [RomPatcher.js](https://www.marcrobledo.com/RomPatcher.js/). Combined they make up the Pokémon Fire GPS ROM hack. 

## Finding location files
The location file (pokefirered.csv) is the file that links real world locations to areas in the game. I have created one for my home town, and will be happy to add any new ones for other places to this project from anyone willing to share. Look inside the [locationfiles](locationfiles/) folder to see if one already exists for where you live - if not, you can always make your own! 

## Creating location files
The location file is a list of areas and their locations. Start by copying an existing one. The header looks like this:

#Area Id, In game location, Real life location, Latitude, Longitude, Radius

Each value/column is separated by commas (and an optional space). The first two are id and in-game name, which you don't edit. The third one is the name of a real life location where the player needs to go unlock that area (character limit 25). The fourth and fifth column are the latitude and longitude (in decimal), and the last one is radius (in meters) - how close to that spot that player needs to be. Theoretically you can pick any locations, but if you intend for your file to be useful here are some tips and considerations to keep in mind:

1. Choose distinct places. The player that sees the name needs to know where it is, or be able to figure it out extremely easily via Google Maps. If your name could refer to several places then be specific or, if they are next to each other, make a circle that encompasses both.
2. Accessibility matters. Don't pick anywhere that's private, restricted or otherwise unsuitable for someone to go and play Pokémon.
3. Pick a generous radius. GPS position is not always exact, so make the area a bit larger than you think is needed. 
4. Google maps has some helpful features. You can right click any pin on the map and the first option is a coordinate pair you can copy. This menu also lets you measure distances. If no suitable pin exists, you can click anywhere on the map to create your own.
5. Try to have places in order. The order areas are listed in is the order a player will encounter them if following the Bulbapedia walkthrough, so try not to have each area too far from the last.
6. Have places reasonable distances apart. Best is if each travel is a walking distance from connected places. Just don't have them so close that the circles overlap.
7. If possible, try to match the vibes. Ocean or coastal routes could be at the beach or other water feature, or Celadon City near a shopping mall. You know your area best, so try to have fun with it!

If you have created a file, please share it! You can sent it to me via DM or email, or just make a pull request. You can give yourself credit in the file if you want, just make an extra row starting with a #.