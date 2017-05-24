package com.ft.up

enum Cluster implements Serializable {
  DELIVERY, PUBLISHING

  @Override
  String toString() {
    return name().toLowerCase()
  }
}
