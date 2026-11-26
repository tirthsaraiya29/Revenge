package com.revenge.youlyplus

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerOrderTest {

  private val servers = listOf("a", "b", "c")

  @Test
  fun `null keeps original order`() {
    assertEquals(servers, promoteServer(null, servers))
  }

  @Test
  fun `known last server is promoted without duplication`() {
    assertEquals(listOf("b", "a", "c"), promoteServer("b", servers))
    assertEquals(listOf("c", "a", "b"), promoteServer("c", servers))
  }

  @Test
  fun `unknown last server keeps original order`() {
    assertEquals(servers, promoteServer("z", servers))
  }
}
