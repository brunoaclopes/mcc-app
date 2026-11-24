# Roadmap: making it the machine people reach for

Most of the time a machine like this ends up making soup. What makes people use it for more is less effort deciding
what to cook, fewer touches with dirty hands, and results they can trust. This page lists what is done and what is
worth doing next, roughly in order of value for the effort.

## Done

- **Any-vegetable soup** (Programs): weight, how firm, chunky or blended at three levels, optional onion base; water,
  time and blending are worked out (`recipe/Soup.kt`).
- **Heated steps reach their temperature**: a step whose time runs out before the bowl is hot keeps heating, within
  a cap, instead of moving on with a lukewarm bowl (`RecipeRunner`).
- **Hands-free weighing**: weigh steps move on by themselves once the amount has been in the bowl for 2 s (Settings
  → Machine; never starts the machine).
- **Keep warm** after a heated recipe: 30 min at 70 °C, held to start, from the "finished" screen.
- **Stopwatch runs** in Manual (time 0:00), as on the stock app.
- **Home Assistant stays connected** with the screen off, and can wake the screen.

## Next

1. **"What can I make?"**: pick what is in the fridge (chips for the most common ingredients) and get the recipes
   that use them, from the cached recipes, on the machine and the phone. With the AI server, a "make something
   with these" button that writes a recipe from the list (the same import path, so the same limits apply).
2. **Suggestions on Home**: three cards chosen for the moment: quick ones on weekdays (under 30 min), the season,
   favourites not cooked for a while, and never the same as yesterday. Needs a small cooking history (recipe id,
   date, servings), kept on the machine.
3. **"Ready at" for the whole recipe**: the remaining machine time plus an estimate for the preparation steps, on
   the Cook screen, the phone and Home Assistant (`finish` of the recipe, not only of the step).
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

## Not planned

Starting the machine on a timer or from outside without someone at the machine: every start stays a hold on the
machine (or a single permitted run, see [HOME-ASSISTANT.md](HOME-ASSISTANT.md#remote-start-opt-in)).
