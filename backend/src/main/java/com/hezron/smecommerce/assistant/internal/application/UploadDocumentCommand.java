package com.hezron.smecommerce.assistant.internal.application;

/**
 * An owner's upload, read from the request but not yet checked.
 *
 * @param fileName as the browser sent it; only its ending (.txt or .md) is used
 * @param bytes    the file's contents
 */
public record UploadDocumentCommand(String title, String fileName, byte[] bytes) {
}
