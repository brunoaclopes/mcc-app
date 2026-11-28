# Roadmap: making it the machine people reach for

Most of the time a machine like this ends up making soup. What makes people use it for more is less effort deciding
what to cook, fewer touches with dirty hands, and results they can trust. This page lists what is done and what is
worth doing next, roughly in order of value for the effort.

## Done

- **Long programs** (slow cooking, sous-vide, yoghurt) and the **Monsieur Cuisine Smart programs** in online recipes,
  run as machine steps; heating now needs 50 g in the bowl instead of 200 g (the stock app has no minimum).
- **Monsieur Cuisine online recipes** (Settings, off by default): the official recipes of the Monsieur Cuisine
  website, listed live and downloaded when opened, without duplicating the recipe server's (`recipe/online/McOnline.kt`).
  **Include member recipes** adds the recipes members published there (source `member`, tagged "Community", with a
  note on the recipe that its settings are not checked by Monsieur Cuisine).
- **Any-vegetable soup** (Programs): how firm, chunky or blended at three levels, optional onion base; put in as many
  vegetables as you like and the machine weighs them and works out water, time and portions (`recipe/Soup.kt`).
- **Heated steps reach their temperature**: a step whose time runs out before the bowl is hot keeps heating, within
  a cap, instead of moving on with a lukewarm bowl (`RecipeRunner`).
- **Hands-free weighing**: weigh steps move on by themselves once the amount has been in the bowl for 2 s (Settings
  → Machine; never starts the machine).
- **Keep warm** after a heated recipe: 30 min at 70 °C, held to start, from the "finished" screen.
- **Stopwatch runs** in Manual (time 0:00), as on the stock app.
- **Home Assistant stays connected** with the screen off, and can wake the screen; it also gets the Wi-Fi signal
  and a `keep_warm` program flag.
- **"What can I make?"**: the Recipes search matches ingredients of every recipe; "chicken, rice" finds recipes
  with both.
- **Cooking history**: finished recipes are remembered; "Cooked lately" in Recipes.
- **"Ready about" for the whole recipe**: on the Cook screen and as `recipe_finish` for Home Assistant.
- **Accessories** (`recipe/Accessories.kt`): recipes list the original accessories they use, steps show what to put
  in or take out, and the machine asks for the mixer attachment to be out before a step faster than speed 4. The
  steaming programs follow the Monsieur Cuisine steamer (deep basket in place of the lid, 1 litre of water).
- **Favourites** on Home, in Recipes and on the phone.
- **Steps before cooking** on the machine: the recipe page has Ingredients and Steps tabs (tap or swipe), the
  steps at a glance as on the phone.
- **Moulinex Companion recipes** (Settings, off by default): about 500 recipes from moulinex.pt, converted for this
  machine when opened (`recipe/online/Moulinex.kt`).
- **Improve with AI**: an improved copy of any recipe, shown instead of the original.

## Next

1. **"Make something with these"**: with the AI server, write a recipe from a list of ingredients (the same import
   path, so the same limits apply), and ingredient chips for the most common ones. The same search on the phone.
2. **Suggestions on Home**: three cards chosen for the moment: quick ones on weekdays (under 30 min), the season,
   favourites not cooked for a while (favourites are in), never the same as yesterday, using the cooking history.
3. **"Ready about" on the phone**, from `recipe_finish`.
4. **More generators like the soup**, where the weight decides the rest: risotto by rice weight (stock, wine,
   stirring in reverse), béchamel and custards by milk weight, bread dough by flour weight (water, salt, yeast,
   kneading time), jam by fruit weight (sugar, setting temperature), yoghurt (37 °C, hours).
5. **Notes after cooking**: a quick "how was it?" (too salty, needed more time, perfect) stored with the recipe; the
   next time the recipe opens, the note shows, and the AI import can use the notes for similar recipes.
6. **Weekly plan on the phone**: pick recipes for the week, one merged shopping list with quantities added up by
   ingredient, and the plan on the machine's Home.
7. **Weight-aware steps**: when a weigh step ends well off its amount (a bigger squash than planned), offer to scale
   the times and liquids of the following steps, the way servings are scaled now.
8. **Announcements**: "add the pasta now" through Home Assistant on a kitchen speaker (the `needs_you` event already
   carries the text), and a gentle repeat if nobody reacts.
9. **Cleaning reminder**: after a recipe with sauces or dough, offer the matching cleaning program.
10. **Wi-Fi and tablet settings in the app**, as on the stock app: Wi-Fi on/off, scan, connect with a password, forget
    a network, show the address; screen brightness. Needs testing at the machine, since a wrong step takes the tablet
    off the network it is managed over.

## Not planned

Starting the machine on a timer or from outside without someone at the machine: every start stays a hold on the
machine (or a single permitted run, see [HOME-ASSISTANT.md](HOME-ASSISTANT.md#remote-start-opt-in)).
