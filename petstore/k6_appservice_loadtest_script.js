import http from 'k6/http';
import { sleep } from 'k6';

export default function () {
    http.get('https://demo-productservice-centralindia.thankfulriver-5ae45caf.centralindia.azurecontainerapps.io/swagger-ui/#/product-api-controller/infoUsingGET');
    sleep(1); // Adjust the sleep time as needed
}
