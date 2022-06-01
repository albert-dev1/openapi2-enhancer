```
swagger2openapi openapi2.json -o openapi3.yaml
openapi-generator-cli generate -i openapi3.yaml -g typescript-fetch -o typescript-fetch --skip-validate-spec 
```