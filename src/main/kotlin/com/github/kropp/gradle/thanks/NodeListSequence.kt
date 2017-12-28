package com.github.kropp.gradle.thanks

import org.w3c.dom.Node
import org.w3c.dom.NodeList


fun NodeList.asSequence() = NodeListSequence(this)

class NodeListSequence(private val nodes: NodeList) : Sequence<Node> {
  override fun iterator() = NodeListIterator(nodes)
}

class NodeListIterator(private val nodes: NodeList) : Iterator<Node> {
  private var i = 0
  override fun hasNext() = nodes.length > i
  override fun next(): Node = nodes.item(i++)
}
