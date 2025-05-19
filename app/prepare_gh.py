import json
import os
import requests
import tempfile

def get_guest_holes(endpoint_url, cert_url, cert_priv_key_path):
    cert_request = requests.get(url=cert_url, timeout=10)
    cert_request.raise_for_status()
    with tempfile.NamedTemporaryFile() as cert_file:
        cert_file.write(cert_request.text.encode('utf-8'))
        cert_file.flush()
        response = requests.get(url=endpoint_url, cert=(cert_file.name, cert_priv_key_path), timeout=10)
        response.raise_for_status()
        guest_holes = response.json()['Guestholes']
        return json.dumps(guest_holes)

def main():
    if 'CI_RUNNER_INTERNAL_CERTIFICATE_URL' not in os.environ or 'CI_RUNNER_INTERNAL_PRIVATE_KEY' not in os.environ:
        print("Cert vars not found. Skipping guest holes.")
        return
    cert_url = os.environ['CI_RUNNER_INTERNAL_CERTIFICATE_URL']
    cert_priv_path = os.environ['CI_RUNNER_INTERNAL_PRIVATE_KEY']
    endpoint_url = os.environ['GUEST_HOLE_ENDPOINT_URL']
    json = get_guest_holes(endpoint_url, cert_url, cert_priv_path)
    print("GuestHoleServers.json:")
    print(json)

    with open('app/src/main/assets/GuestHoleServers.json', 'w', encoding='utf-8') as guest_holes_file:
        guest_holes_file.write(json)

if __name__ == "__main__":
    main()
