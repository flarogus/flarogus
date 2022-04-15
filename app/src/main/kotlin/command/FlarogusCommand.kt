package flarogus.command

open class FlarogusCommand(val name: String) {
	var action: (Callback.() -> Unit)? = null
	val arguments: Arguments? = null

	fun action(action: Callback.() -> Unit) {
		this.action = action
	}

	inline fun arguments(builder: Arguments.() -> Unit) {
		if (arguments = null) arguments = Arguments()
		arguments!!.apply(builder)
	}
} 
