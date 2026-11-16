import { readOnlyIteration } from './common.js';

export const options = {
    vus: 2,
    duration: '30s',
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    readOnlyIteration();
}
