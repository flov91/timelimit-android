// https://www.rfc-editor.org/rfc/rfc5280.html#appendix-A.1
// https://letsencrypt.org/docs/a-warm-welcome-to-asn1-and-der/
// https://www.ietf.org/rfc/rfc5480.txt

/*
SubjectPublicKeyInfo  ::=  SEQUENCE  {
     algorithm            AlgorithmIdentifier,
     subjectPublicKey     BIT STRING  }


AlgorithmIdentifier  ::=  SEQUENCE  {
     algorithm               OBJECT IDENTIFIER,
     parameters              ANY DEFINED BY algorithm OPTIONAL  }
                                -- contains a value of the type
                                -- registered for use with the
                                -- algorithm object identifier value

id-ecPublicKey OBJECT IDENTIFIER ::= {
     iso(1) member-body(2) us(840) ansi-X9-62(10045) keyType(2) 1 }

secp256r1 OBJECT IDENTIFIER ::= {
     iso(1) member-body(2) us(840) ansi-X9-62(10045) curves(3)
     prime(1) 7 }
*/

function encodeValue(type, valueBuffer) {
  const length = valueBuffer.length

  if (length >= 128) throw new Error('long encoding not supported')

  const typeBuffer = Buffer.from([type])
  const lengthBuffer = Buffer.from([length])

  return Buffer.concat([typeBuffer, lengthBuffer, valueBuffer])
}

function encodeBitstring(valueBuffer) {
  return encodeValue(3, Buffer.concat([Buffer.from([0 /* no unused bits */]), valueBuffer]))
}

function encodeOid(value) {
  const numbers = [
    value[0] * 40 + value[1],
    ...value.slice(2)
  ]

  const result = []

  for (let number of numbers) {
    const tempResult = []
    while (number) { tempResult.push(128 | (number % 128)); number = Math.floor(number / 128) }
    tempResult.reverse()
    tempResult[tempResult.length - 1] &= 127

    for (const item of tempResult) result.push(item)
  }

  return encodeValue(6, Buffer.from(result))
}

function encodeSequence(itemBuffers) {
  return encodeValue(0x30, Buffer.concat(itemBuffers))
}

function encodeAlogrithmIdentifier() {
  return encodeSequence([
    encodeOid([1, 2, 840, 10045, 2, 1]),
    encodeOid([1, 2, 840, 10045, 3, 1, 7])
  ])
}

function encodePublicKeyInfo() {
  const dummyKey = Buffer.alloc(65)
  for (let i = 0; i < dummyKey.length; i++) dummyKey.writeUInt8(255, i)

  return encodeSequence([
    encodeAlogrithmIdentifier(),
    encodeBitstring(dummyKey)
  ])
}

const result = encodePublicKeyInfo()

// compare result with https://github.com/ashtuchkin/u2f/blob/2e45ea40acd8c3ad6c113cd1b4e0558acc4cda3a/index.js#L21
if (result.toString('hex') !== '3059301306072a8648ce3d020106082a8648ce3d030107034200' + ('ff'.repeat(65))) throw new Error()

const prefix = result.slice(0, result.length - 65)

console.log(prefix.toString('hex'))
console.log(prefix.toString('base64'))

const prefixNumbers = []; for (let i = 0; i < prefix.length; i++) prefixNumbers.push(prefix.readInt8(i))
const kotlinByteArray = 'byteArrayOf(' + prefixNumbers.join(', ') + ')'

console.log(kotlinByteArray)
