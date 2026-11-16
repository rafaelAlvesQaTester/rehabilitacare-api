import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:18081';
const ACCESS_TOKEN = __ENV.K6_ACCESS_TOKEN;

if (!ACCESS_TOKEN) {
    throw new Error('Set K6_ACCESS_TOKEN in the process environment; do not print or store it.');
}

function isHealthy(response) {
    if (response.status !== 200) {
        return false;
    }

    try {
        return response.json('status') === 'UP';
    } catch {
        return false;
    }
}

function isEmptyPatientPage(response) {
    if (response.status !== 200) {
        return false;
    }

    try {
        const page = response.json();
        return Array.isArray(page.content)
            && page.content.length === 0
            && page.totalElements === 0;
    } catch {
        return false;
    }
}

export function readOnlyIteration() {
    const health = http.get(`${BASE_URL}/api/v1/health`, {
        tags: { name: 'health' },
        timeout: '10s',
    });
    check(health, {
        'health endpoint returns 200 and UP': isHealthy,
    });

    const patients = http.get(`${BASE_URL}/api/v1/patients?page=0&size=20`, {
        headers: {
            Authorization: `Bearer ${ACCESS_TOKEN}`,
        },
        tags: { name: 'patients_list' },
        timeout: '10s',
    });
    check(patients, {
        'authenticated patient list is empty and returns 200': isEmptyPatientPage,
    });

    sleep(1);
}
