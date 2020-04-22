package com.ft.jenkins

import java.security.MessageDigest

static String md5(String input) {
  MessageDigest.getInstance("MD5").digest(input.bytes).encodeHex().toString()
}

