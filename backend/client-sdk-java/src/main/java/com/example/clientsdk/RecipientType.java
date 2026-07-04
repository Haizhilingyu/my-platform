package com.example.clientsdk;

/** Recipient scope. {@code USER}=specific user id; {@code ROLE}=all users under a role;
 * {@code UNIT}=all users under a unit (including descendants). */
public enum RecipientType {
  USER,
  ROLE,
  UNIT
}
