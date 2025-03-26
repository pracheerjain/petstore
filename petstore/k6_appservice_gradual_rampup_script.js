import http from 'k6/http';
import { sleep } from 'k6';

export let options = {
    stages: [
        { duration: '1m', target: 10 },   // Ramp-up to 10 users over 1 minute
        { duration: '5m', target: 10 },   // Stay at 10 users for 5 minutes
    ],
};

export default function () {
    http.get('https://demo-productservice-centralindia.thankfulriver-5ae45caf.centralindia.azurecontainerapps.io/swagger-ui/#/product-api-controller/infoUsingGET');
    sleep(1); // Adjust the sleep time as needed
}
