package network.stratus;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Originally from https://github.com/dropbox/dropbox-api-content-hasher
 */
public final class Hash extends MessageDigest implements Cloneable  {
    private MessageDigest overallHasher;
    private MessageDigest blockHasher;
    private int blockPos = 0;

    public static final int BLOCK_SIZE = 4 * 1024 * 1024;
    static final char[] HEX_DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    public Hash()  {
        this(newSha256Hasher(), newSha256Hasher(), 0);
    }

    private Hash(MessageDigest overallHasher, MessageDigest blockHasher, int blockPos) {
        super("Dropbox-Content-Hash");
        this.overallHasher = overallHasher;
        this.blockHasher = blockHasher;
        this.blockPos = blockPos;
    }

    public String hash(Path path) {
        try {
            byte[] buf = new byte[1024];
            InputStream input = new FileInputStream(path.toString());
            try {
                while(true) {
                    int n = input.read(buf);
                    if(n < 0) break;
                    update(buf, 0, n);
                }
            } finally {
                input.close();
            }
            byte[] rawHash = digest();
            char[] buff = new char[2 * rawHash.length];
            int i = 0;
            for(byte b : rawHash) {
                buff[i++] = HEX_DIGITS[(b & 0xf0) >>> 4];
                buff[i++] = HEX_DIGITS[b & 0x0f];
            }
            return new String(buff);
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        return "";
    }

    @Override
    protected void engineUpdate(byte input) {
        finishBlockIfFull();

        blockHasher.update(input);
        blockPos += 1;
    }

    @Override
    protected int engineGetDigestLength() {
        return overallHasher.getDigestLength();
    }

    @Override
    protected void engineUpdate(byte[] input, int offset, int len) {
        int inputEnd = offset + len;
        while (offset < inputEnd) {
            finishBlockIfFull();

            int spaceInBlock = BLOCK_SIZE - this.blockPos;
            int inputPartEnd = Math.min(inputEnd, offset+spaceInBlock);
            int inputPartLength = inputPartEnd - offset;
            blockHasher.update(input, offset, inputPartLength);

            blockPos += inputPartLength;
            offset += inputPartLength;
        }
    }

    @Override
    protected void engineUpdate(ByteBuffer input) {
        int inputEnd = input.limit();
        while (input.position() < inputEnd) {
            finishBlockIfFull();

            int spaceInBlock = BLOCK_SIZE - this.blockPos;
            int inputPartEnd = Math.min(inputEnd, input.position()+spaceInBlock);
            int inputPartLength = inputPartEnd - input.position();
            input.limit(inputPartEnd);
            blockHasher.update(input);

            blockPos += inputPartLength;
            input.position(inputPartEnd);
        }
    }

    @Override
    protected byte[] engineDigest() {
        finishBlockIfNonEmpty();
        return overallHasher.digest();
    }

    @Override
    protected int engineDigest(byte[] buf, int offset, int len) throws DigestException {
        finishBlockIfNonEmpty();
        return overallHasher.digest(buf, offset, len);
    }

    @Override
    protected void engineReset() {
        this.overallHasher.reset();
        this.blockHasher.reset();
        this.blockPos = 0;
    }

    @Override
    public Hash clone() throws CloneNotSupportedException {
        Hash clone = (Hash) super.clone();
        clone.overallHasher = (MessageDigest) clone.overallHasher.clone();
        clone.blockHasher = (MessageDigest) clone.blockHasher.clone();
        return clone;
    }

    private void finishBlock() {
        overallHasher.update(blockHasher.digest());
        blockPos = 0;
    }

    private void finishBlockIfFull() {
        if (blockPos == BLOCK_SIZE) {
            finishBlock();
        }
    }

    private void finishBlockIfNonEmpty() {
        if (blockPos > 0) {
            finishBlock();
        }
    }

    static MessageDigest newSha256Hasher() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError("Couldn't create SHA-256 hasher");
        }
    }
}
