package org.bouncycastle.asn1;

import java.io.IOException;

/**
 * The DLSequence encodes a SEQUENCE using definite length form.
 */
public class DLSequence
    extends ASN1Sequence
{
    private int bodyLength = -1;

    /**
     * Create an empty sequence
     */
    public DLSequence()
    {
    }

    /**
     * create a sequence containing one object
     * @param obj the object to go in the sequence.
     */
    public DLSequence(
        ASN1Encodable obj)
    {
        super(obj);
    }

    /**
     * create a sequence containing a vector of objects.
     * @param v the vector of objects to make up the sequence.
     */
    public DLSequence(
        ASN1EncodableVector v)
    {
        super(v);
    }

    /**
     * create a sequence containing an array of objects.
     * @param array the array of objects to make up the sequence.
     */
    public DLSequence(
        ASN1Encodable[] array)
    {
        super(array);
    }

    DLSequence(ASN1Encodable[] array, boolean clone)
    {
        super(array, clone);
    }

    private int getBodyLength()
        throws IOException
    {
        if (bodyLength < 0)
        {
            int count = elements.length;
            int totalLength = 0;

            for (int i = 0; i < count; ++i)
            {
                ASN1Primitive dlObject = elements[i].toASN1Primitive().toDLObject();
                totalLength += dlObject.encodedLength();
            }

            this.bodyLength = totalLength;
        }

        return bodyLength;
    }

    int encodedLength()
        throws IOException
    {
        int length = getBodyLength();

        return 1 + StreamUtil.calculateBodyLength(length) + length;
    }

    /**
     * A note on the implementation:
     * <p>
     * As DL requires the constructed, definite-length model to
     * be used for structured types, this varies slightly from the
     * ASN.1 descriptions given. Rather than just outputting SEQUENCE,
     * we also have to specify CONSTRUCTED, and the objects length.
     */
    void encode(ASN1OutputStream out) throws IOException
    {
        int length = getBodyLength();

        out.write(BERTags.SEQUENCE | BERTags.CONSTRUCTED);
        out.writeLength(length);

        ASN1OutputStream dlOut = out.getDLSubStream();

        int count = elements.length;
        for (int i = 0; i < count; ++i)
        {
            dlOut.writeObject(elements[i]);
        }
    }

    ASN1Primitive toDLObject()
    {
        return this;
    }
}