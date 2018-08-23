package com.ft.jenkins

import java.security.MessageDigest

class HashUtil {

  public static String md5(String input) {
    return MessageDigest.getInstance("MD5").digest(input.bytes).encodeHex().toString()
  }
}
