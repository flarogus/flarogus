package flarogus.multiverse.npc

import kotlin.random.*

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

inline fun Node.repeat(times: Int, node: Node) = RepeatNode(times, node);

inline fun Node.repeat(minTimes: Int, maxTimes: Int, node: Node) = RandomRepeatNode(minTimes, maxTimes, node);

inline fun Node.repeat(times: Int, node: String) = RepeatNode(times, TerminalNode(node));

inline fun Node.repeat(minTimes: Int, maxTimes: Int, node: String) = RandomRepeatNode(minTimes, maxTimes, TerminalNode(node));

/** Adds a trailing string to the node */
infix fun FollowNode.and(node: String) = FollowNode(node, null).also { this.next = it };

/** Adds a trailing node to the node */
infix fun <T: Node> FollowNode.and(node: T) = DoubleNode(node, null).also { this.next = it };

/** Adds a trailing string to the node */
infix fun DoubleNode.and(node: String) = FollowNode(node, null).also { this.second = it };

/** Adds a trailing node to the node */
infix fun <T: Node> DoubleNode.and(node: T) = DoubleNode(node, null).also { this.second = it };

/*
 * classes region
 */

abstract class Node {
	/** Constructs a phrase */
	abstract fun construct(builder: StringBuilder, origin: String)
	
	/** Counts the amount of possible combinations */
	open fun count() = 1
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
	
	override open fun count() = if (next != null) next!!.count() else 1;
	
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
	
	override open fun count() = (if (first != null) first!!.count() else 1) * (if (second != null) second!!.count() else 1);
	
}

open class RepeatNode(open val times: Int, open var repeat: Node) : Node() {
	override open fun construct(builder: StringBuilder, origin: String) {
		for (i in 1..times) repeat.construct(builder, origin)
	}
	
	override fun count() = repeat.count()
}

open class RandomRepeatNode(open var minTimes: Int, open var maxTimes: Int, repeat: Node) : RepeatNode(maxTimes, repeat) {
	override open val times get() = Random.nextInt(minTimes, maxTimes)
	
	override fun count() = (maxTimes - minTimes) * repeat.count()
}

open class RandomNode(override open val children: MutableList<Node>) : TreeNode<Node>() {
	override open fun construct(builder: StringBuilder, origin: String) = selectChild().construct(builder, origin);
	
	override open fun count(): Int {
		var c = 0;
		children.forEach { c += it.count() }
		return c
	}
	
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
	
	override open fun count(): Int {
		var c = 0;
		children.forEach { c += it.second.count() }
		return c
	}
	
	/** Adds the if-then-string pair to this conditional node */
	open infix fun If.then(result: String) = FollowNode(result, null).also { this@ConditionalNode.children.add(this to it) };
	
	/** Adds the if-then-node pair to this conditional node */
	open infix fun <T : Node> If.then(result: T) = DoubleNode(result, null).also { this@ConditionalNode.children.add(this to it) };
}
