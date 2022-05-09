import { deepStrictEqual } from 'assert'
import { get } from 'https'
import { parse } from 'csv-parse/sync'
import { deflateSync } from 'zlib'
import { writeFileSync } from 'fs'
import { dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))

function pullString(url) {
  return new Promise((resolve, reject) => {
    get(url, (res) => {
      if (res.statusCode !== 200) {
        reject(new Error('unexpected status code'))
        res.resume()
        return
      }

      const buffers = []

      res
        .on('data', buffers.push.bind(buffers))
        .on('end', () => {
          resolve(Buffer.concat(buffers))
        })
        .on('error', reject)
    }).on('error', reject)
  })
}

async function main() {
  const data =
    (await pullString('https://storage.googleapis.com/play_public/supported_devices.csv'))
    .toString('utf-16le')

  const parsedData = parse(data, {})
  
  deepStrictEqual(parsedData[0], ['﻿Retail Branding', 'Marketing Name', 'Device', 'Model'])

  const rows = parsedData.slice(1).map((row) => {
    if (row.length !== 4) throw new Error(`got ${row.length} columns: ${row}`)

    const [brand, marketingName, device, model] = row

    return { brand, marketingName, device, model }
  })

  const relevantRows = rows.filter((row) => row.marketingName !== row.model)

  relevantRows.sort((a, b) => {
    if (a.device < b.device) return -1
    else if (a.device > b.device) return 1
    else if (a.model < b.model) return -1
    else if (a.model > b.model) return 1
    else return 0
  })

  const relevantStringsSet = new Set()

  relevantRows.forEach((row) => {
    relevantStringsSet.add(row.device)
    relevantStringsSet.add(row.model)
    relevantStringsSet.add(row.marketingName)
  })

  const relevantStrings = Array.from(relevantStringsSet)
  relevantStrings.sort(); relevantStrings.reverse()

  const stringToIndex = new Map()

  let relevantStringPool = Buffer.alloc(0)
  let relevantStringPoolAddCounter = 0
  for (const str of relevantStrings) {
    if (stringToIndex.has(str)) continue

    const oldIndex = relevantStringPool.indexOf(str)
  
    if (oldIndex === -1) {
      stringToIndex.set(str, relevantStringPool.length)

      relevantStringPool = Buffer.concat([relevantStringPool, Buffer.from(str)])
      relevantStringPoolAddCounter++
    } else {
      stringToIndex.set(str, oldIndex)
    }
  }

  // 4 bytes per string: start index (3 byte) + length (1 byte)
  // 3 strings (device, model, marketingName)
  const deviceInfoBuffer = Buffer.alloc(relevantRows.length * (3 * 4))
  
  const writeString = (outputIndex, str) => {
    const startIndex = stringToIndex.get(str)

    if (startIndex === undefined) throw new Error()
    if (startIndex > 0xffffff) throw new Error()
    deviceInfoBuffer.writeUInt8((startIndex >> 16) & 0xff, outputIndex * 4 + 0)
    deviceInfoBuffer.writeUInt8((startIndex >> 8) & 0xff, outputIndex * 4 + 1)
    deviceInfoBuffer.writeUInt8(startIndex & 0xff, outputIndex * 4 + 2)
    deviceInfoBuffer.writeUInt8(str.length, outputIndex * 4 + 3)
  }

  relevantRows.forEach((row, index) => {
    const outputIndex = index * 3

    writeString(outputIndex + 0, row.device)
    writeString(outputIndex + 1, row.model)
    writeString(outputIndex + 2, row.marketingName)
  })

  const relevantStringBuffer = Buffer.from(relevantStringPool, 'utf8')

  const lengthDataBuffer = Buffer.alloc(8)
  lengthDataBuffer.writeUInt32BE(relevantStringBuffer.length, 0)
  lengthDataBuffer.writeUInt32BE(relevantRows.length, 4)

  const resultData = Buffer.concat([lengthDataBuffer, relevantStringBuffer, deviceInfoBuffer])
  const resultDataCompressed = deflateSync(resultData, {})

  writeFileSync(resolve(__dirname, '../app/src/main/assets/device-names.bin'), resultDataCompressed)

  console.log({
    rows: rows.length,
    relevantRows: relevantRows.length,
    relevantStringCount: relevantStrings.length,
    relevantStringPoolAddCounter,
    relevantStringPoolLength: relevantStringPool.length,
    resultDataLength: resultData.length,
    resultDataCompressedLength: resultDataCompressed.length,
    compressedBytesPerDevices: resultDataCompressed.length / relevantRows.length
  })
}

main().catch((ex) => {
  console.warn(ex)

  process.exit(1)
})
