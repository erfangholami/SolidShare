# Sharing in Solid Share
**An standard for sharing resources in Solid project**

**Authors**
- [Yashar PourMohamad](https://github.com/yasharpm/)
- [Erfan Gholami](https://github.com/erfangholami)

## Abstract
Solid Share aims to focus on the access control capability of Solid Project to provide an easy and smooth experience for users to share their resources and receive shares from others.

The following describes how the share feature is designed so that it can be further developed in the next stages. And also enable any other enthusiastic client developers to be able to use this as a references for seamless experience for users access various Solid clients.

## Criteria
We come up with the criteria of how a good resource sharing and share receiving experience should be:
1. Any file or folder must be shareable.
2. Users must be able to see everything they have shared and all the shares they have received.
3. It must be possible to create shares or revoked them at any moment.
4. Restarting the client or using another client must not result in losing the track of shares.
5. Any Solid client must be able to recognize the shares and provide the same features.

## Scope
Solid project uses both [WAC](https://solidproject.org/TR/wac) and [ACP](https://solid.github.io/authorization-panel/acp-specification/) protocols for managing access to resources. Some of the most used Solid servers implemented one or both protocols shown bellow:

| Server   | Has WAC  | Has ACP  |
| -------- | -------- | -------- |
| [Community Solid Server](https://communitysolidserver.github.io/CommunitySolidServer/latest/) | YES | YES |
| [Inrupt Solid Server](https://www.inrupt.com/products/enterprise-solid-server) | YES | YES |
| [Trinpod](https://graphmetrix.com/trinpod-server) | YES | NO |
| [Manas](https://manomayam.github.io/manas/introduction.html) | YES | YES |
| [Node Solid Server](https://github.com/nodeSolidServer/node-solid-server) | YES | NO |
| [PHP Solid Server](https://pdsinterop.org/php-solid-server/) | YES | NO |

As all of them have the support for WAC, we decided to utilize it as the underlying access control system to create and receive shares in Solid Share.

This will be our scope:
1. Sharing with and receiving shares from other Solid users.
2. Sharing a resource to the public.
3. Share access is one of read, append, or edit.
4. Sharing privately with people outside the Solid ecosystem is not possible.

## How it works
The client maintains an updated list of all the given and received shares. These lists are privately stored in specially named resource files on the pod itself. These lists are kept on an update-only-when-looked-at basis. This mechanism finds a good balance between data availability and data validity. The information "might" change after the user looks at them. But these changes will merely occur, unless they start using it heavily and on multiple clients at the same time. More over, this mechanism allows for multiple clients to cooperate on keeping the lists updated.

The shares are created by the sharing user either impulsively or by request from the receiving user. A QR code that points to the shared resource is then presented to the receiving user. They scan the QR code with their client and this is how items are added to the received shares list. Upon each attempt to access the received share or see the list, the received shares are verified by the client to make sure the share still exists.

Received shares are stored privately in a RDF-resource named `received_shares.ttl` inside a container named `.shares` on the pod root.

Here is a sample of the received shares list resource:
```
@prefix acl: <http://www.w3.org/ns/auth/acl#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .

<http://www.w3.org/People/Tim-Berners-Lee/card#i> acl:Write <url_to_resource> .	# Tim has allowed the user to edit or delete the resource
<http://www.w3.org/People/Michiel-De-Jong/card#i> acl:Append <url_to_resource> .	# Michiel has allowed the user to add information to the resoource
<http://www.w3.org/People/Erfan-Gholami/card#i> acl:Read <url_to_resource> .	# Erfan has allowed the user to read the resource
```
The subject of the triplet declares who has shared the resource. The predicate specifies the access mode. And the object points to the shared resource.

Every time a share is created, updated or deleted, the given shares list is updated accordingly. Similarly whenever the list of given shares are accessed, the client scans all the resources in the pod recursively and updates the given shares list if required.

Given shares are stored privately in a RDF-resource named `given_shares.ttl` inside a container named `.shares` on the pod root.

Here is a sample of the given shares list resource:
```
@prefix acl: <http://www.w3.org/ns/auth/acl#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .

foaf:Agent acl:Read <url_to_resoource> .                                    	# The public can read the resource
<http://www.w3.org/People/Tim-Berners-Lee/card#i> acl:Write <url_to_resource> .	# Tim can edit or delete the resource
<http://www.w3.org/People/Michiel-De-Jong/card#i> acl:Append <url_to_resource> .	# Michiel can add information to the resoource
```
The subject of the triplet declares the receiver of the share. The predicate specifies the access mode. And the object points to the shared resource.

## User's experience
Imagine a scenario in which **Alice** wants to receive a share of resource `X` from **Bob**:
1. **Alice** shares her webID with **Bob**.
2. **Bob** opens resource `X`'s properties from his Solid Share app.
3. **Bob** selects "share" and inputs **Alice**'s webID as the receiver and also specifies the access level for **Alice**.
4. **Bob**'s Solid Share app sends the new access control rule to **Bob**'s pod service provider and also adds a record to the `/.share/given_shares.ttl` on his pod.
5. Now **Bob** can show the QR code pointing to resource `X` to **Alice**.
6. **Alice** scans the QR code and Solid Share app on her device immediately opens. **Alice** can now see that she has access to resource `X`.
7. After confirmation, **Alice**'s Solid Share app adds a record to the `/.share/received.shares.ttl` on her pod.
8. From now on, whenever **Alice** opens the "received shares" screen on the Solid Share app, she can see that she has access to `X` and can interact with it.

## Algorithms
Below are the algorithms that are used. Each one is based on an user action or a call from another function.

### Retrieving a given share
When the user opens a file's or a directory's properties. We call a function to discover those who this resource has been shared with.
```
function getGivenShares(resource)
	shares = getStoredGivenSharesForResource(resource)
	emit shares
	shares = queryGivenSharesForResourceViaWac(resource)
	emit shares
	

function getStoredGivenSharesForResource(resource)
	shares = []
	Access given_shares.ttl
	for each triplet in given_shares.ttl:
		if trplet.object == resource:
			shares.append(triplet)
	return shares
	

function queryGivenSharesForResourceViaWac(resource)
	shares = []
	authorizationRules = query access control list for given resource
	for each rule in authorizationRules:
		triplet = convert rule to a share trplet
		shares.append(triplet)
	emit shares
	updateGivenSharesForResource(resource, givenShares)
	

function updateGivenSharesForResource(resource, givenShares)
	Access given_shares.ttl
	for each trplet in given_shares.ttl:
		if trplet.object == resource:
			remove trplet from the given_shares.ttl
	for each triplet in givenShares:
		Append trplet to given_shares.ttl
```
The stored given shares is queried first for immediate response. And then we actually check the share status via WAC and update both the UI and the list if required.

### Retrieving the list of given shares
When the user wants to access the list of given shares, we call a function to retrieve the list.
```
function getGivenShares()
	shares = getStoredGivenShares()
	emit shares
	shares = queryGivenSharesViaWac()
	emit shares
	

function getStoredGivenShares()
	shares = []
	Access given_shares.ttl
	for each triplet in given_shares.ttl:
		shares.append(triplet)
	return shares
	

function queryGivenSharesViaWac()
	shares = []
	root = get root container
	shares.append(queryGivenSharesRecursively(root))
	emit shares
	updateGivenShares(shares)
	

function queryGivenSharesRecursively(resource)
	shares = queryGivenSharesForResourceViaWac(resource)
	if resource is a container:
		for each resource r inside the container:
			shares.append(queryGivenSharesRecursively(r))
	return shares
	

function updateGivenShares(shares)
	Access given_shares.ttl
	Remove all triplets inside given_shares.ttl
	for each triplet in shares:
		Append triplet to given_shares.ttl
```
The list of given shares is immediately displayed via reading the stored given shares list. Afterwards, the time consuming act of recursively reading the access mode of all the resources inside the pod is started. Upon completion the list is updated in the UI and the result will replace the previous stored list.

### Creating a share
When the user creates a share, we call a function to add it to the list.
```
function createShare(resource, accessMode, receiver or None if public)
	if the receiver is None:
		receiver = 'http://xmlns.com/foaf/0.1/Agent'
	share = Create a given share triplet using resource, accessMode and receiver
	ensureShareInTheGivenSharesList(share)
	Send request to add rule to the resource's access control list

	
function ensureShareInTheGivenSharesList(share)
	Access given_shares.ttl
	for each triplet in given_shares.ttl:
		if triplet.resource == share.resource and triplet.receiver == share.receiver:
			if triplet.accessMode != share.accessMode:
				Remove triplet from given_shares.ttl
				break
			else:
				return
	Append share to given_shares.ttl
```
ACL is set via WAC. Then if any similar shares for the resource is found in the stored given shares list, it is removed and finally the new share will be inserted instead.

### Updating a share
When the user updates the access mode of a share, we call a function to update it.
```
function updateShare(resource, accessMode, receiver or None if public)
	if the receiver is None:
		receiver = 'http://xmlns.com/foaf/0.1/Agent'
	share = Create a given share triplet using resource, accessMode and receiver
	updateShareInGivenSharesList(share)
	Send request to update the rule on the resource's access control list

	
function updateShareInGivenSharesList(share)
	Access given_shares.ttl
	for each triplet in given_shares.ttl:
		if triplet.resource == share.resource and triplet.receiver == share.receiver:
			Remove triplet from given_shares.ttl
			Append share to given_shares.ttl
			return
```
ACL is set via WAC. Then the previous share for the resource is found in the stored given shares list and updated.

### Removing a share
When the user deletes a share, we call a function to remove it.
```
function removeShare(resource, receiver or None if public)
	if the receiver is None:
		receiver = 'http://xmlns.com/foaf/0.1/Agent'
	removeShareFromGivenSharesList(resource, receiver)
	Send request to delete the rule on the resource's access control list


function removeShareFromGivenSharesList(resource, receiver)
	Access given_shares.ttl
	for each triplet in given_shares.ttl:
		if triplet.resource == resource and triplet.receiver == receiver:
			Remove triplet from given_shares.ttl
			return
```
ACL rule is removed via WAC. Then the share is found in the stored given shares list and removed.

### Receiving a share
The user receives a URI that points to a resource either by scanning a QR code or direct text. We then call a function to check access and update the list of received shares.
```
function addReceivedShare(resource)
	accessMode = Query access mode for the resource via WAC
	if accessMode is None:
		Fail with no access exception
		return
	owner = Query owner of the resource
	share = Create a received share triplet using resource, accessMode and owner
	ensureShareInTheReceivedSharesList(share)


function ensureShareInTheReceivedSharesList(share)
	Access received_shares.ttl
	for each triplet in received_shares.ttl:
		if triplet.resource == share.resource and triplet.owner == share.owner:
			if triplet.accessMode != share.accessMode:
				Remove triplet from received_shares.ttl
				break
			else:
				return
	Append share to received_shares.ttl
```
The properties of the share are queried via WAC. Then the share is added to the list of received shares if not exists or updated if it already exists.

### Retrieving the list of received shares
When the user wants to access the list of received shares, we call a function to retrieve the list.
```
function getReceivedShares()
	shares = getStoredReceivedShares()
	emit shares
	valid_shares = validateReceivedShares(shares)
	emit valid_shares
	updateReceivedShares(valid_shares)


function getStoredReceivedShares()
	shares = []
	Access received_shares.ttl
	for each triplet in received_shares.ttl:
			shares.append(triplet)
	return shares


function validateReceivedShares(shares)
	valid_shares = []
	for each share in shares:
		accessMode = Query access mode for share.resource via WAC
		if accessMode is None:
			continue
		owner = Query owner of the resource
		valid_share = Create a received share triplet using resource, accessMode and owner
		valid_shares.append(valid_share)
	return valid_shares


function updateReceivedShares(shares)
	Access received_shares.ttl
	Remove all triplets inside received_shares.ttl
	for each triplet in shares:
		Append triplet to received_shares.ttl
```
The list of received shares is quickly retrieved from the stored received shares and presented to the user. Then, each share is validated to make sure is still exists with the same properties.
The verified list is then updated on the UI and used to refresh the list of stored received shares as well.
