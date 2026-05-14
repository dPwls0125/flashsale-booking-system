const autocannon = require('autocannon');

const instance = autocannon({
  url: 'http://localhost:8080/api/booking/1',
  connections: 10,
  duration: 5, // 5초로 단축
  method: 'POST',
  body: JSON.stringify({ paymentMethods: ['CREDIT_CARD'] }),
  headers: {
    'Content-Type': 'application/json'
  },
  setupClient: (client) => {
    client.on('request', (req) => {
      req.headers['X-Member-Id'] = Math.floor(Math.random() * 1000000).toString();
      req.headers['Idempotency-Key'] = Math.random().toString(36).substring(2) + Date.now();
    });
  }
}, (err, result) => {
  if (err) {
    console.error(err);
  } else {
    console.log(autocannon.printResult(result));
  }
});
