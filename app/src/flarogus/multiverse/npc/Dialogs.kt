package flarogus.multiverse.npc

typealias Builder = (builder: StringBuilder, origin: String) -> Unit
fun interface If { operator fun invoke(origin: String): Boolean }

/* Expected usage:
import kotlin.random.*
import flarogus.multiverse.npc.*

val tree = buildDialog {
	- condition {
		If { it.contains("sus") } then "when the " and random {
			- "amogus is sus"
			- "the"
			- "you"
		} and " amogg" and "!";
		If { it.contains("scream") } then run { "aaa".repeat(Random.nextInt(1, 10)) } and "!"
		//else
		If { true } then "wtf none of these conditions were met illegal"
	}
}
*/

/** Constructs the root of a dialog tree */
inline fun buildDialog(block: FollowNode.() -> Unit) = RootNode().apply {
	block()
};

/** Creates an executable node that's supposed to append some text */
fun Node.runBuild(executable: Builder) = ExecutableNode(executable);

/** Creates an executable node that's supposed to return some text which will be appended to the builder */
inline fun Node.run(crossinline executable: (origin: String) -> String?) = runBuild { builder, origin ->
	builder.append(executable(origin))
};

/** Creates a node that delegates it's construction to one of it's child */
inline fun Node.random(block: RandomNode.() -> Unit) = RandomNode(ArrayList<Node>(8)).apply {
	block()
};

/** Creates a node that, upon construction, finds the first true condition and delegates the construction to it */
inline fun Node.condition(block: ConditionalNode.() -> Unit) = ConditionalNode(ArrayList<Pair<If, Node>>(5)).apply {
	block()
};

/** Adds a trailing string to the node */
infix fun FollowNode.and(node: String) = FollowNode(node, null).also { this.next = it };

/** Adds a trailing node to the node */
infix fun <T: Node> FollowNode.and(node: T) = DoubleNode(node, null).also { this.next = it };

/** Adds a trailing string to the node */
infix fun DoubleNode.and(node: String) = FollowNode(node, null).also { this.second = it };

/** Adds a trailing node to the node */
infix fun <T: Node> DoubleNode.and(node: T) = DoubleNode(node, null).also { this.second = it };

//classes region
abstract class Node {
	abstract fun construct(builder: StringBuilder, origin: String)
}

abstract class TreeNode<T> : Node() {
	abstract val children: MutableList<T>
}

open class ExecutableNode(val executable: Builder) : Node() {
	override open fun construct(builder: StringBuilder, origin: String) = executable(builder, origin)
}

open class TerminalNode(val string: String) : Node() {
	override open fun construct(builder: StringBuilder, origin: String) {
		builder.append(string)
	}
}

open class FollowNode(string: String, var next: Node?) : TerminalNode(string) {
	override open fun construct(builder: StringBuilder, origin: String) {
		builder.append(string)
		next?.construct(builder, origin)
	}
	
	open operator fun String.unaryMinus() = FollowNode(this, null).also { this@FollowNode.next = it };
	
	open operator fun <T: Node> T.unaryMinus() = DoubleNode(this, null).also { this@FollowNode.next = it };
}


open class RootNode : FollowNode("", null) {
	open fun construct(origin: String) = StringBuilder(100).also { construct(it, origin) }.toString()
}

open class DoubleNode(var first: Node?, var second: Node?) : Node() {
	override open fun construct(builder: StringBuilder, origin: String) {
		if (first != null) first!!.construct(builder, origin)
		if (second != null) second!!.construct(builder, origin)
	}
	
}

open class RandomNode(override open val children: MutableList<Node>) : TreeNode<Node>() {
	override open fun construct(builder: StringBuilder, origin: String) = selectChild().construct(builder, origin);
	
	open fun selectChild(): Node = children.random();
	
	/** Adds a child string node */
	open operator fun String.unaryMinus() = FollowNode(this, null).also { this@RandomNode.children.add(it) };
	
	/** Adds a child node */
	open operator fun <T : Node> T.unaryMinus() = DoubleNode(this, null).also { this@RandomNode.children.add(it) };
}

open class ConditionalNode(override open val children: MutableList<Pair<If, Node>>) : TreeNode<Pair<If, Node>>() {
	override open fun construct(builder: StringBuilder, origin: String) {
		children.find { it.first(origin) }?.second?.construct(builder, origin)
	}
	
	/** Adds the if-then-string pair to this conditional node */
	open infix fun If.then(result: String) = FollowNode(result, null).also { this@ConditionalNode.children.add(this to it) };
	
	/** Adds the if-then-node pair to this conditional node */
	open infix fun <T : Node> If.then(result: T) = DoubleNode(result, null).also { this@ConditionalNode.children.add(this to it) };
}
