# Stormcraft Quick Start Guide

## ✅ Plugin Successfully Loaded!

Your Stormcraft plugin is now running on your Paper server.

## Basic Commands

### Check Status
```
/storm
```
Shows current storm status and time until next storm.

### Force Start a Storm (for testing)
```
/storm start medium
```
Starts a medium-intensity storm immediately.

### Stop Active Storm
```
/storm stop
```
Ends the current storm.

### Set Next Storm Timer
```
/storm next 30
```
Sets the next storm to occur in 30 seconds.

### View Storm Weights
```
/storm weights
```
Shows the probability distribution of storm types.

### Reload Configuration
```
/storm reload
```
Reloads config.yml and messages.yml without restarting the server.

### Toggle Storm UI
```
/storm ui
```
Cycles through UI display modes (Action Bar / Boss Bar / Both / Disabled).

### List All Storms
```
/storms
```
Shows all active traveling storms (if enabled).

## Testing the Plugin

1. **Give yourself admin permissions:**
   ```
   /op YourUsername
   ```

2. **Start a test storm:**
   ```
   /storm start short
   ```

3. **Test exposure mechanics:**
   - Stand outside → You should take damage
   - Go inside (under solid blocks) → Damage stops
   - Go back outside → Damage resumes

4. **Test different storm types:**
   ```
   /storm start short     # 60s, 2 HP/s
   /storm start medium    # 150s, 4 HP/s, blindness, slowness
   /storm start long      # 240s, 6 HP/s, blindness, slowness, lightning
   ```

## Configuration

Edit these files in `plugins/Stormcraft/`:

### `config.yml`
- Storm type weights (probability)
- Damage per second for each type
- Storm duration
- Countdown settings
- Exposure check settings
- Enabled worlds

### `messages.yml`
- Customize all chat messages
- Use MiniMessage format for colors
- Available placeholders: `{time}`, `{dps}`, `{type}`, `{duration}`, `{next}`

After editing, use `/storm reload` to apply changes.

## Features

✅ **Automatic Storm Scheduling** - Random delays between storms
✅ **Countdown Warnings** - Chat announcements before landfall
✅ **Exposure-Based Damage** - Only affects players outside
✅ **Multiple Storm Types** - Short/weak, medium, long/dangerous
✅ **Persistence** - Storms resume after server restarts
✅ **Effects** - Blindness, slowness, lightning strikes
✅ **WorldGuard Support** - Protect specific regions (requires WorldGuard)
✅ **PlaceholderAPI** - Use placeholders in other plugins (requires PlaceholderAPI)

## Permissions

- `stormcraft.view` - See storm announcements (default: everyone)
- `stormcraft.admin` - All admin commands (default: ops)
- `stormcraft.admin.start` - Force start storms
- `stormcraft.admin.stop` - Stop storms
- `stormcraft.admin.next` - Set next storm timer
- `stormcraft.admin.reload` - Reload config
- `stormcraft.admin.test` - Test damage on self

## WorldGuard Integration (Optional)

If you have WorldGuard installed, you can protect specific regions from storms:

```
/region flag <region-name> stormcraft-protect allow
```

Note: The flag registration warning at startup is normal and doesn't affect functionality.

## PlaceholderAPI Integration (Optional)

If you have PlaceholderAPI installed, you can use these placeholders:

- `%stormcraft_status%` - Idle/Countdown/Active
- `%stormcraft_time_left%` - Time remaining
- `%stormcraft_type%` - Current storm type
- `%stormcraft_dps%` - Damage per second
- `%stormcraft_next_storm%` - Time until next storm

## Troubleshooting

### "No active storm to stop"
- Use `/storm` to check if a storm is active
- Use `/storm start` to force start one for testing

### Players not taking damage
- Check if they're in Creative/Spectator mode (configurable in config.yml)
- Make sure they're standing outside with sky access
- Verify the world is enabled in config.yml

### Changes not applying
- Use `/storm reload` after editing config files
- Check server console for any config errors

## Need Help?

- Full documentation: See `STORMCRAFT_CONTEXT.md`
- Comprehensive testing: See `TEST_PLAN.md`
- Development guide: See `CLAUDE.md`

Enjoy your dangerous storms! ⛈️