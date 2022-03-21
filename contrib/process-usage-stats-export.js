const { resolve } = require('path')
const { readFileSync } = require('fs')

const data = JSON.parse(readFileSync(resolve(__dirname, 'timelimit-usage-stats-export.json')))
const events = data.events
  .map((item) => item.native)
  .map((event) => {
    // fix wrong exported instanceIds
    const binary = Buffer.from(event.binary, 'base64')
    const instanceId = binary.readUInt32LE(8)

    return {...event, instanceId}
  })

const instanceIdToApp = new Map()

for (event of events) {
  function printStatus(message) {
    let apps = []

    for(const app of instanceIdToApp.values()) {
      apps.push(app)
    }

    console.log(new Date(event.timestamp) + ': ' + message + ' -> ' + (apps.join(', ') || 'none'))
  }

  if (event.type === 27 /* reboot */) {
    instanceIdToApp.clear()

    printStatus('reboot')
  } else if (event.type === 1 /* move to foreground */) {
    instanceIdToApp.set(event.instanceId, event.packageName + ':' + event.className)

    printStatus('start ' + event.instanceId)
  } else if (event.type === 2 /* move to background */) {
    instanceIdToApp.delete(event.instanceId)

    printStatus('stop ' + event.instanceId)
  } else if (event.type === 23 /* stopped */) {
    instanceIdToApp.delete(event.instanceId)

    printStatus('kill ' + event.instanceId)
  }
}
