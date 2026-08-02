# Session Guidance design

Each temporary entry contains:

- timestamp;
- recommendation type and text;
- severity and confidence;
- latest accepted event IDs;
- compact evidence summary;
- compact critical channel-resolution summary.

The history signature excludes rapidly changing raw values. It includes recommendation type/text, severity/confidence, and selected critical-channel names. This prevents high-rate UI refreshes from creating noise while still recording a meaningful channel becoming resolved or unresolved.

Maximum retained entries: 100. Oldest entries are dropped first. The model exists only in memory and is cleared on reset or plugin restart.
