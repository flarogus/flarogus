package flarogus.multiverse.npc

typealias IF = (origin: String) -> Boolean
typealias Builder = (builder: StringBuilder, origin: String) -> Unit

/* Expected usage:
import flarogus.multiverse.npc.*

buildDialog {
	+ random {
		- "hi"
		- condition { b, o ->
			IF { o.contains("bye") } then "goodbye"
		}
		- "amogus" + " aaaa " + random {
			- " is sus"
			- ""
		}
	}
}
*/

fun buildDialog(begin: String = "", block: FollowNode.() -> Unit) = FollowNode(begin, null).apply {
	block()
};

fun Node.run(executable: Builder) = ExecutableNode(executable);

fun Node.random(block: RandomNode.() -> Unit) = RandomNode(ArrayList<Node>(8)).apply {
	block()
};

fun Node.condition(block: ConditionalNode.() -> Unit) = ConditionalNode(ArrayList<Pair<IF, Node>>(5)).apply {
	block()
};

operator fun FollowNode.plus(node: String) = FollowNode(node, null).also { this.next = it };
	
operator fun <T: Node> FollowNode.plus(node: T) = node.also { this.next = it };

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
	
	open operator fun String.unaryPlus() = FollowNode(this, null).also { this@FollowNode.next = it };
	
	open operator fun <T: Node> T.unaryPlus() = this.also { this@FollowNode.next = it };
}

open class RandomNode(override open val children: MutableList<Node>) : TreeNode<Node>() {
	override open fun construct(builder: StringBuilder, origin: String) = selectChild().construct(builder, origin);
	
	open fun selectChild(): Node = children.random();
	
	open operator fun String.unaryMinus() = FollowNode(this, null).also { this@RandomNode.children.add(it) };
	
	open operator fun <T : Node> T.unaryMinus() = this.also { this@RandomNode.children.add(it) };
}

open class ConditionalNode(override open val children: MutableList<Pair<IF, Node>>) : TreeNode<Pair<IF, Node>>() {
	override open fun construct(builder: StringBuilder, origin: String) {
		children.find { it.first(origin) }?.second?.construct(builder, origin)
	}

	open infix fun IF.then(result: String) = FollowNode(result, null).also { this@ConditionalNode.children.add(this to it) };
	
	open infix fun <T : Node> IF.then(result: T) = result.also { this@ConditionalNode.children.add(this to it) };
}
