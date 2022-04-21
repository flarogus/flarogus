package flarogus.command

import dev.kord.core.entity.*

open class FlarogusCommand<R>(val name: String) {
	var action: (Callback<R>.() -> Unit)? = null
	var arguments: Arguments? = null

	var description: String = "No description"

	var requiredArguments = 0
		protected set

	open fun action(action: Callback<R>.() -> Unit) {
		this.action = action
	}

	inline fun arguments(builder: Arguments.() -> Unit) {
		if (arguments == null) arguments = Arguments()
		arguments!!.apply(builder)

		//ensure they follow the "required-optional" order
		var opt = false
		arguments.positional.forEach {
			if (opt && !it.optional) throw IllegalArgumentException("Mandatory arguments must come first")
			if (it.optional) opt = true
		}

		requiredArguments = arguments.positional.fold(0) { t, arg -> if (arg.optional) t else t + 1 }
	}

	open operator fun invoke(message: Message?, argsOverride: String): Callback<R> {
		return Callback<R>(this, message).also { useCallback(it, argsOverride) }	
	}

	/** Invokes this command for a message and returns the result of this command (if there's any) */
	open operator fun invoke(args: String): R? {
		return Callback<R>(this).also { 
			it.replyResult = false
			useCallback(it, args) 
		}.result
	}

	/** Invokes this command for a messagee */
	open operator fun invoke(message: Message): Callback<R> = invoke(message, message.content)

	protected open fun useCallback(callback: Callback<R>, args: String, argumentOffset: Int = 0) {
		if (arguments != null) {
			ArgumentDecoder(arguments!!, callback, args, argumentOffset).decode()
		}
		action?.invoke(callback)
	}
} 
