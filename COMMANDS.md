# SkyScope commands

Every command starts with `/skyscope`. `<code>`, `<uuid>` and `<item>` are values you supply.

| Command | Function |
|---|---|
| `/skyscope` | Open the dashboard |
| `/skyscope help` | List all commands |
| `/skyscope status` | Show linked-account and feed status |
| `/skyscope link <code>` | Link using your website account code |
| `/skyscope sync` | Refresh your account filters |
| `/skyscope reconnect` | Reconnect your account feed |
| `/skyscope flip` | Open the flip browser |
| `/skyscope filters` | Edit your filters |
| `/skyscope pause` | Pause new flip alerts |
| `/skyscope resume` | Resume flip alerts |
| `/skyscope clear` | Clear the current flip queue |
| `/skyscope queue` | List queued flips |
| `/skyscope session` | Show session counts and estimated profits |
| `/skyscope why` | Show recent server rejection reasons |
| `/skyscope dismiss <uuid>` | Remove a queued auction |
| `/skyscope blacklist list` | Show excluded items |
| `/skyscope blacklist add <item>` | Exclude an item name or item ID |
| `/skyscope blacklist remove <item>` | Remove an exclusion |
| `/skyscope blacklist clear` | Clear all item exclusions |
| `/skyscope reset` | Reset your filters to defaults |
| `/skyscope quickbuy` | Quick Buy overlay status |
| `/skyscope quickbuy status` | Quick Buy overlay status |
| `/skyscope quickbuy on` | Quick Buy overlay on |
| `/skyscope quickbuy off` | Quick Buy overlay off |
| `/skyscope autobuy` | automatic buying status |
| `/skyscope autobuy status` | automatic buying status |
| `/skyscope autobuy on` | automatic buying on |
| `/skyscope autobuy off` | automatic buying off |
| `/skyscope chat` | chat alerts status |
| `/skyscope chat status` | chat alerts status |
| `/skyscope chat on` | chat alerts on |
| `/skyscope chat off` | chat alerts off |
| `/skyscope chat compact` | Set chat layout to compact |
| `/skyscope chat detailed` | Set chat layout to detailed |
| `/skyscope chat fees on` | Show/hide fees in chat alerts |
| `/skyscope chat fees off` | Show/hide fees in chat alerts |
| `/skyscope chat evidence on` | Show/hide evidence in chat alerts |
| `/skyscope chat evidence off` | Show/hide evidence in chat alerts |
| `/skyscope chat selltime on` | Show/hide selltime in chat alerts |
| `/skyscope chat selltime off` | Show/hide selltime in chat alerts |
| `/skyscope chat uuid on` | Show/hide uuid in chat alerts |
| `/skyscope chat uuid off` | Show/hide uuid in chat alerts |
| `/skyscope chat category on` | Show/hide category in chat alerts |
| `/skyscope chat category off` | Show/hide category in chat alerts |
| `/skyscope chat rarity on` | Show/hide rarity in chat alerts |
| `/skyscope chat rarity off` | Show/hide rarity in chat alerts |
| `/skyscope sort quality` | Sort queued flips by quality |
| `/skyscope sort profit` | Sort queued flips by profit |
| `/skyscope sort roi` | Sort queued flips by roi |
| `/skyscope sort fastest` | Sort queued flips by fastest |
| `/skyscope sort safe` | Sort queued flips by safe |
| `/skyscope sort newest` | Sort queued flips by newest |
| `/skyscope ranking balanced` | Apply balanced ranking weights |
| `/skyscope ranking profit` | Apply profit ranking weights |
| `/skyscope ranking liquid` | Apply liquid ranking weights |
| `/skyscope ranking safe` | Apply safe ranking weights |
| `/skyscope preset beginner` | Apply beginner filter preset |
| `/skyscope preset balanced` | Apply balanced filter preset |
| `/skyscope preset aggressive` | Apply aggressive filter preset |

Right Shift: dashboard. B: flip browser. Both bindings can be changed in Minecraft Controls.

Use `/skyscope instantmedian on` or `/skyscope instantmedian off` (default OFF). With this option enabled, listings below 100,000 coins whose seven-day **item-ID median** exceeds 3,000,000 coins appear before pricing finishes. Variants are pooled in that reference; it is not a resale target. The initial notification shows profit pending and can trigger immediate Auto Buy when your Auto Buy toggle is ON, before pricing confirms profit. A later update shows canonical pricing or its rejection/unresolved reason. Only online delivery is supported, with a 180-second result-tracking window. Ordinary validated flips retain normal filters and purchase controls.
