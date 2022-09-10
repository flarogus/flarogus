# Flarogus bot
Flarogus is an open-source sussy discord bot that doesn't have a specific purpose
and instead does various things.

![amogus!](https://cdn.discordapp.com/attachments/732665247302942730/940852980712898591/flarsus.png)

# The multiverse
The main feature flarogus provides is the ability to connect to the Multiverse, a network of 20+ guilds.
Messages sent in one of the multiversal channels are retranslated to every other channel,
and so are the changes made to these messages.

There are many subfeatures the multiverse has, like NPCs, moderators, admins, name overrides, etc., but that is not covered in this document.

In order for a channel to connect to the multiverse, the following conditions have to be met:
* The channel must have `multiverse` in its name
* The guild this channel is in must be whitelisted (use `!flarogus report` or join the official server by using `!flarogus server` to contact the admins).
* The guild must not be blacklisted

Users of the multiverse must follow the rules, which can be seen by executing `!flarogus multiverse rules`.
Violating a rule can get you warned. Violating different rules gives a different number of warning points.
Gathering 5 warning points will get you temporarily banned for until your warns expire and your warning score drops below 5.

If a multiversal admin or moderator considers your message inappropriate, they can delete it or apply another kind of punishment.
A severe violation can get you or even your whole guild banned on-spot, so try to not go nuts there.

Deleting or editing the original message also deletes or edits it in other channels.
This action can be performed by guild-specific authorities too if they consider that the message sent in their specific channel is inappropriate.
If you can't access the original message, you still can delete it from all channels by replying to it with
`!flarogus multiverse deletereply`. Normal users can only delete their own messages this way.

# Overcomplicated command system
Flarogus has a very complex tree-like command system, which as of now has only been mastered by no more than 5 people.
Commands are nested one inside another, with "!flarogus" being the root command (and bot's prefix at the same time).

In order to invoke a command, you have to type its full name, which consists of its own name,
preceded by the full name of it'd parent (which too can have a parent, whose full name has to come before)
and pass the required and optional arguments to it, e.g. `!flarogus multiverse rules`.
Sometimes command names can get ridiculously long, but that should be ok.

There are tree commands, which only hold children, and terminal commands, which actually do things.

Every tree command has a `help` command.
Invoking this command without any arguments shows the list of subcommands the tree command has and their argument signatures.
Passing the name of a terminal subcommand to the help command shows a full help,
which includes the arguments/flags the command accepts, their signatures, etc.
E.g. typing "!flarogus help` shows the list of subcommands of the root command,
and typing `!flarogus util help userinfo` shows the full help for the `!flarogus util userinfo` command.

## Command arguments
Everything passed after the full name of a command is considered an argument.
Arguments are separated by spaces, but you can pass an argument containing spaces by enclosing it in quotation marks.
E.g. `!flarogus help "multiword argument"` passes `multiword argument` as a single argument to the `!flarogus help` command
(this command will fail, but that doesn't matter).

There are also raw arguments — everything passed after a `<<` (double less-than sign) is considered a single string argument,
which ends with the end of the message.

In addiction to normal arguments, there are flags. A flag begins with a double minus sign, e.g. `--force`.
They should be passed as separate arguments.
If a command accepts any flags, their full names are mentioned both in the full and brief description of that command.

In addiction to long names, flags also can have short aliases. 
They begin with a single minus sign, followed by 1~any count of symbols, each symbol representing a different short flag.
e.g. `!flarogus help -bar` is the same as `!flarogus help -b -a -r` (note that the help command doesn't accept these flags).

## Argument types
The full help message also shows argument types.
In general, most arguments passed to commands are either strings, numbers, or user ids,
however, depending, on the argument type, additional checks may be performed.

For example, if a command accepts a "guild" or "multiversal-guild" as an argument type,
you must pass the ID of a valid guild, if it accepts a "user" or "multiversal-user", you must pass the id of a valid user or mention them.
If you pass an argument of a wrong type (this includes invalid IDs), the command won't get invoked at all.

## Command errors
If you attempt to invoke a command with invalid syntax, invoke a non-existent command, pass an invalid argument/flag, etc.,
an error message telling you what's wrong ane highlighting the error place will appear.

If there's a runtime error during the execution of a command, a command-specific message may appear,
its format may vary from command to command.
